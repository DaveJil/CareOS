import { ForbiddenException, Injectable } from '@nestjs/common';
import { PaymentStatus, Role, TriageReviewStatus, TriageUrgency } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';
import { AuditService } from '../audit/audit.service';
import { RequestUser } from '../auth/current-user.decorator';

@Injectable()
export class AdminService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly audit: AuditService,
  ) {}

  async listPatients(user: RequestUser) {
    this.assertSuperAdmin(user);
    return this.prisma.user.findMany({
      where: { role: Role.patient },
      select: {
        id: true,
        email: true,
        phone: true,
        createdAt: true,
        patientProfile: true,
      },
      orderBy: { createdAt: 'desc' },
      take: 100,
    });
  }

  async listClinicians(user: RequestUser) {
    this.assertSuperAdmin(user);
    return this.prisma.user.findMany({
      where: { role: Role.clinician },
      select: {
        id: true,
        email: true,
        phone: true,
        createdAt: true,
        clinicianProfile: true,
      },
      orderBy: { createdAt: 'desc' },
      take: 100,
    });
  }

  async approveClinician(user: RequestUser, clinicianId: string) {
    this.assertSuperAdmin(user);
    const approved = await this.prisma.clinicianProfile.update({
      where: { userId: clinicianId },
      data: { approved: true },
    });

    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'admin.clinician.approve',
      target: `clinician:${clinicianId}`,
    });

    return approved;
  }

  async highUrgencyQueue(user: RequestUser) {
    this.assertSuperAdmin(user);
    return this.prisma.triageCase.findMany({
      where: {
        urgency: TriageUrgency.high,
        reviewStatus: TriageReviewStatus.pending,
      },
      orderBy: { createdAt: 'asc' },
      take: 100,
    });
  }

  async paymentSummary(user: RequestUser) {
    this.assertSuperAdmin(user);
    const payments = await this.prisma.payment.findMany({
      orderBy: { createdAt: 'desc' },
      take: 100,
    });
    const totals = payments.reduce(
      (summary, payment) => {
        summary.count += 1;
        summary.byStatus[payment.status] = (summary.byStatus[payment.status] ?? 0) + 1;
        if (payment.status === PaymentStatus.paid) {
          summary.paidKobo += payment.amountKobo;
        }
        return summary;
      },
      {
        count: 0,
        paidKobo: 0,
        byStatus: {} as Partial<Record<PaymentStatus, number>>,
      },
    );

    return { totals, payments };
  }

  async auditCsv(user: RequestUser) {
    this.assertSuperAdmin(user);
    const entries = await this.prisma.auditEntry.findMany({
      orderBy: { createdAt: 'desc' },
      take: 500,
    });
    const header = ['createdAt', 'actorId', 'actorRole', 'action', 'target', 'metadata'];
    const rows = entries.map((entry) => [
      entry.createdAt.toISOString(),
      entry.actorId ?? '',
      entry.actorRole ?? '',
      entry.action,
      entry.target,
      JSON.stringify(entry.metadata ?? {}),
    ]);
    return [header, ...rows].map((row) => row.map(csvEscape).join(',')).join('\n');
  }

  private assertSuperAdmin(user: RequestUser) {
    if (user.role !== Role.super_admin) {
      throw new ForbiddenException('Only super admins can access admin operations.');
    }
  }
}

function csvEscape(value: string): string {
  if (!/[",\n]/.test(value)) {
    return value;
  }
  return `"${value.replaceAll('"', '""')}"`;
}
