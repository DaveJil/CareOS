import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { BookingStatus, NotificationChannel, Prisma, Role } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';
import { AuditService } from '../audit/audit.service';
import { RequestUser } from '../auth/current-user.decorator';
import { NotificationsService } from '../notifications/notifications.service';
import {
  CancelBookingDto,
  CreateBookingDto,
  SearchSpecialistsQueryDto,
  SendChatMessageDto,
  UpsertClinicianProfileDto,
} from './dto/telehealth.dto';
import { VideoSessionProvider } from './video-session.provider';

const CONSULT_MINUTES = 30;

@Injectable()
export class TelehealthService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly video: VideoSessionProvider,
    private readonly notifications: NotificationsService,
    private readonly audit: AuditService,
  ) {}

  async upsertClinicianProfile(user: RequestUser, dto: UpsertClinicianProfileDto) {
    if (user.role !== Role.clinician && user.role !== Role.super_admin) {
      throw new ForbiddenException('Only clinicians can manage clinician profiles.');
    }

    const profile = await this.prisma.clinicianProfile.upsert({
      where: { userId: user.id },
      create: {
        userId: user.id,
        displayName: dto.displayName,
        specialty: dto.specialty,
        bio: dto.bio,
        priceKobo: dto.priceKobo,
        availability: dto.availability as Prisma.InputJsonValue,
        approved: dto.approved ?? false,
      },
      update: {
        displayName: dto.displayName,
        specialty: dto.specialty,
        bio: dto.bio,
        priceKobo: dto.priceKobo,
        availability: dto.availability as Prisma.InputJsonValue,
        ...(dto.approved === undefined ? {} : { approved: dto.approved }),
      },
    });

    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'telehealth.clinician_profile.upsert',
      target: `clinician_profile:${profile.id}`,
    });

    return profile;
  }

  async searchSpecialists(query: SearchSpecialistsQueryDto) {
    const profiles = await this.prisma.clinicianProfile.findMany({
      where: {
        approved: true,
        ...(query.specialty
          ? { specialty: { contains: query.specialty, mode: 'insensitive' } }
          : {}),
      },
      include: { user: { select: { id: true, email: true, phone: true } } },
      orderBy: { displayName: 'asc' },
    });

    if (!query.startsAt) {
      return profiles;
    }

    const startsAt = new Date(query.startsAt);
    const available = [];
    for (const profile of profiles) {
      const conflict = await this.hasConflict(profile.userId, startsAt, this.endsAt(startsAt));
      if (!conflict) {
        available.push(profile);
      }
    }
    return available;
  }

  async createBooking(user: RequestUser, dto: CreateBookingDto) {
    if (user.role !== Role.patient) {
      throw new ForbiddenException('Only patients can book consultations.');
    }

    const clinicianProfile = await this.prisma.clinicianProfile.findUnique({
      where: { userId: dto.clinicianId },
    });
    if (!clinicianProfile || !clinicianProfile.approved) {
      throw new NotFoundException('Approved clinician not found.');
    }

    const startsAt = new Date(dto.startsAt);
    const endsAt = this.endsAt(startsAt);
    if (await this.hasConflict(dto.clinicianId, startsAt, endsAt)) {
      throw new BadRequestException('Clinician is not available at that time.');
    }

    const booking = await this.prisma.booking.create({
      data: {
        patientId: user.id,
        clinicianId: dto.clinicianId,
        startsAt,
        endsAt,
        priceKobo: clinicianProfile.priceKobo,
        status: BookingStatus.pending_payment,
      },
    });

    const session = await this.video.createSession({
      bookingId: booking.id,
      patientId: user.id,
      clinicianId: dto.clinicianId,
      startsAt,
    });
    const updated = await this.prisma.booking.update({
      where: { id: booking.id },
      data: {
        videoProvider: session.provider,
        videoRoomId: session.roomId,
        videoToken: session.token,
      },
    });

    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'telehealth.booking.create',
      target: `booking:${booking.id}`,
      metadata: {
        clinicianId: dto.clinicianId,
        startsAt: startsAt.toISOString(),
      } as Prisma.InputJsonValue,
    });

    return updated;
  }

  async rescheduleBooking(user: RequestUser, id: string, startsAtInput: string) {
    const booking = await this.getBookingForParticipant(user, id);
    const startsAt = new Date(startsAtInput);
    const endsAt = this.endsAt(startsAt);
    if (await this.hasConflict(booking.clinicianId, startsAt, endsAt, id)) {
      throw new BadRequestException('Clinician is not available at that time.');
    }

    const updated = await this.prisma.booking.update({
      where: { id },
      data: { startsAt, endsAt },
    });
    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'telehealth.booking.reschedule',
      target: `booking:${id}`,
    });
    return updated;
  }

  async cancelBooking(user: RequestUser, id: string, dto: CancelBookingDto) {
    await this.getBookingForParticipant(user, id);
    const updated = await this.prisma.booking.update({
      where: { id },
      data: {
        status: BookingStatus.cancelled,
        cancelReason: dto.reason,
      },
    });
    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'telehealth.booking.cancel',
      target: `booking:${id}`,
    });
    return updated;
  }

  async getBooking(user: RequestUser, id: string) {
    return this.getBookingForParticipant(user, id);
  }

  async confirmBookingPaid(bookingId: string) {
    const booking = await this.prisma.booking.update({
      where: { id: bookingId },
      data: { status: BookingStatus.confirmed },
    });

    await this.notifications.dispatch({
      userId: booking.patientId,
      channel: NotificationChannel.sms,
      target: booking.patientId,
      template: 'booking_confirmation',
      payload: { bookingId: booking.id, startsAt: booking.startsAt.toISOString() },
    });

    return booking;
  }

  async sendChatMessage(user: RequestUser, bookingId: string, dto: SendChatMessageDto) {
    await this.getBookingForParticipant(user, bookingId);
    const message = await this.prisma.consultChatMessage.create({
      data: {
        bookingId,
        senderId: user.id,
        message: dto.message,
      },
    });
    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'telehealth.chat.send',
      target: `booking:${bookingId}`,
    });
    return message;
  }

  async listChatMessages(user: RequestUser, bookingId: string) {
    await this.getBookingForParticipant(user, bookingId);
    return this.prisma.consultChatMessage.findMany({
      where: { bookingId },
      orderBy: { createdAt: 'asc' },
    });
  }

  private async getBookingForParticipant(user: RequestUser, id: string) {
    const booking = await this.prisma.booking.findFirst({
      where: {
        id,
        OR: [{ patientId: user.id }, { clinicianId: user.id }],
      },
    });
    if (!booking) {
      throw new NotFoundException('Booking not found.');
    }
    return booking;
  }

  private async hasConflict(
    clinicianId: string,
    startsAt: Date,
    endsAt: Date,
    excludeBookingId?: string,
  ): Promise<boolean> {
    const conflict = await this.prisma.booking.findFirst({
      where: {
        clinicianId,
        status: { in: [BookingStatus.pending_payment, BookingStatus.confirmed] },
        ...(excludeBookingId ? { id: { not: excludeBookingId } } : {}),
        startsAt: { lt: endsAt },
        endsAt: { gt: startsAt },
      },
    });
    return Boolean(conflict);
  }

  private endsAt(startsAt: Date): Date {
    return new Date(startsAt.getTime() + CONSULT_MINUTES * 60 * 1000);
  }
}
