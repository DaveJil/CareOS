import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  NotFoundException,
  UnauthorizedException,
} from '@nestjs/common';
import { BookingStatus, PaymentStatus, Prisma, Role } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';
import { AuditService } from '../audit/audit.service';
import { RequestUser } from '../auth/current-user.decorator';
import { TelehealthService } from '../telehealth/telehealth.service';
import { CreateChargeDto, PaymentWebhookDto } from './dto/payments.dto';
import { PaymentGatewayProvider } from './payment-gateway.provider';

@Injectable()
export class PaymentsService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly gateway: PaymentGatewayProvider,
    private readonly telehealth: TelehealthService,
    private readonly audit: AuditService,
  ) {}

  async createCharge(user: RequestUser, dto: CreateChargeDto) {
    if (user.role !== Role.patient) {
      throw new ForbiddenException('Only patients can pay for bookings.');
    }

    const existing = await this.prisma.payment.findUnique({
      where: { idempotencyKey: dto.idempotencyKey },
    });
    if (existing) {
      return existing;
    }

    const booking = await this.prisma.booking.findFirst({
      where: {
        id: dto.bookingId,
        patientId: user.id,
        status: BookingStatus.pending_payment,
      },
      include: { patient: { select: { email: true } } },
    });
    if (!booking) {
      throw new NotFoundException('Pending booking not found for payment.');
    }

    const initialized = await this.gateway.initializeCharge({
      bookingId: booking.id,
      amountKobo: booking.priceKobo,
      email: booking.patient.email,
    });

    const payment = await this.prisma.payment.create({
      data: {
        bookingId: booking.id,
        patientId: user.id,
        amountKobo: booking.priceKobo,
        gateway: initialized.gateway,
        providerReference: initialized.providerReference,
        idempotencyKey: dto.idempotencyKey,
        status: PaymentStatus.pending,
      },
    });

    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'payment.charge.create',
      target: `payment:${payment.id}`,
      metadata: { bookingId: booking.id, amountKobo: booking.priceKobo } as Prisma.InputJsonValue,
    });

    return {
      ...payment,
      authorizationUrl: initialized.authorizationUrl,
    };
  }

  async handleWebhook(dto: PaymentWebhookDto, signature?: string) {
    if (!this.gateway.verifyWebhookSignature(dto, signature)) {
      throw new UnauthorizedException('Invalid payment webhook signature.');
    }

    const providerReference = dto.providerReference ?? dto.data?.reference?.toString();
    if (!providerReference) {
      throw new BadRequestException('Payment webhook reference is required.');
    }

    const payment = await this.prisma.payment.findUnique({
      where: { providerReference },
    });
    if (!payment) {
      throw new NotFoundException('Payment not found for webhook.');
    }
    if (payment.status === PaymentStatus.paid) {
      return payment;
    }

    const providerStatus = dto.status ?? dto.data?.status?.toString();
    const status = providerStatus === 'success' ? PaymentStatus.paid : PaymentStatus.failed;
    const updated = await this.prisma.payment.update({
      where: { id: payment.id },
      data: {
        status,
        rawWebhook: (dto.raw ?? dto.data ?? dto) as Prisma.InputJsonValue,
        ...(status === PaymentStatus.paid
          ? {
              paidAt: new Date(),
              receiptNumber: this.receiptNumber(payment.id),
            }
          : {}),
      },
    });

    if (status === PaymentStatus.paid) {
      await this.telehealth.confirmBookingPaid(payment.bookingId);
    }

    await this.audit.record({
      action: 'payment.webhook',
      target: `payment:${payment.id}`,
      metadata: { status, providerReference } as Prisma.InputJsonValue,
    });

    return updated;
  }

  async receipt(user: RequestUser, paymentId: string) {
    const payment = await this.prisma.payment.findFirst({
      where: {
        id: paymentId,
        ...(user.role === Role.patient ? { patientId: user.id } : {}),
      },
      include: { booking: true },
    });
    if (!payment) {
      throw new NotFoundException('Payment not found.');
    }
    if (payment.status !== PaymentStatus.paid) {
      throw new BadRequestException('Receipt is only available after payment is confirmed.');
    }
    return {
      receiptNumber: payment.receiptNumber,
      paymentId: payment.id,
      bookingId: payment.bookingId,
      amountKobo: payment.amountKobo,
      currency: payment.currency,
      paidAt: payment.paidAt,
      gateway: payment.gateway,
    };
  }

  async listPaymentStatus(user: RequestUser) {
    if (user.role !== Role.super_admin) {
      throw new ForbiddenException('Only super admins can view payment status lists.');
    }
    return this.prisma.payment.findMany({
      orderBy: { createdAt: 'desc' },
      take: 100,
    });
  }

  private receiptNumber(paymentId: string): string {
    return `CAREOS-${paymentId.slice(0, 8).toUpperCase()}`;
  }
}
