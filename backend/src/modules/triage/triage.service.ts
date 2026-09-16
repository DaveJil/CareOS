import { ForbiddenException, Injectable, NotFoundException } from '@nestjs/common';
import { Prisma, Role, TriageReviewStatus, TriageUrgency } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';
import { AuditService } from '../audit/audit.service';
import { RequestUser } from '../auth/current-user.decorator';
import { AiTriageProvider } from './ai-triage.provider';
import { SubmitTriageDto, TriageHistoryQueryDto } from './dto/triage.dto';

@Injectable()
export class TriageService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly aiProvider: AiTriageProvider,
    private readonly audit: AuditService,
  ) {}

  async submit(user: RequestUser, dto: SubmitTriageDto) {
    if (user.role !== Role.patient) {
      throw new ForbiddenException('Only patients can submit symptom triage.');
    }

    const result = await this.aiProvider.assess({
      symptomsText: dto.symptomsText,
      questionnaire: dto.questionnaire,
    });
    const reviewStatus =
      result.urgency === TriageUrgency.high
        ? TriageReviewStatus.pending
        : TriageReviewStatus.not_required;

    const triageCase = await this.prisma.triageCase.create({
      data: {
        patientId: user.id,
        symptomsText: dto.symptomsText,
        questionnaire: dto.questionnaire as Prisma.InputJsonValue | undefined,
        urgency: result.urgency,
        suggestedSpecialty: result.suggestedSpecialty,
        recommendedAction: result.recommendedAction,
        riskScore: result.riskScore,
        reviewStatus,
        aiProvider: result.provider,
        aiRationale: result.rationale,
      },
    });

    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'triage.submit',
      target: `triage_case:${triageCase.id}`,
      metadata: {
        urgency: triageCase.urgency,
        riskScore: triageCase.riskScore,
        reviewStatus: triageCase.reviewStatus,
      } as Prisma.InputJsonValue,
    });

    return this.publicCase(triageCase);
  }

  async history(user: RequestUser, query: TriageHistoryQueryDto) {
    if (user.role !== Role.patient) {
      throw new ForbiddenException('Only patients can view their triage history.');
    }

    const urgency = query.urgency?.filter((value): value is TriageUrgency =>
      Object.values(TriageUrgency).includes(value as TriageUrgency),
    );
    const cases = await this.prisma.triageCase.findMany({
      where: {
        patientId: user.id,
        ...(urgency && urgency.length > 0 ? { urgency: { in: urgency } } : {}),
        ...(query.query
          ? {
              symptomsText: {
                contains: query.query,
                mode: 'insensitive',
              },
            }
          : {}),
      },
      orderBy: { createdAt: 'desc' },
    });

    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'triage.history.view',
      target: `patient:${user.id}`,
    });

    return cases.map((triageCase) => this.publicCase(triageCase));
  }

  async clinicianQueue(user: RequestUser) {
    this.assertClinician(user);
    const cases = await this.prisma.triageCase.findMany({
      where: { reviewStatus: TriageReviewStatus.pending },
      orderBy: [{ urgency: 'desc' }, { createdAt: 'asc' }],
    });

    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'triage.review_queue.view',
      target: 'triage_queue:high_urgency',
    });

    return cases.map((triageCase) => this.publicCase(triageCase));
  }

  async markReviewed(user: RequestUser, id: string) {
    this.assertClinician(user);
    const triageCase = await this.prisma.triageCase.findFirst({
      where: { id, reviewStatus: TriageReviewStatus.pending },
    });

    if (!triageCase) {
      throw new NotFoundException('Pending triage case not found.');
    }

    const reviewed = await this.prisma.triageCase.update({
      where: { id },
      data: {
        reviewStatus: TriageReviewStatus.reviewed,
        reviewedById: user.id,
        reviewedAt: new Date(),
      },
    });

    await this.audit.record({
      actor: { id: user.id, role: user.role },
      action: 'triage.reviewed',
      target: `triage_case:${id}`,
    });

    return this.publicCase(reviewed);
  }

  private assertClinician(user: RequestUser) {
    const clinicianRoles: Role[] = [Role.clinician, Role.super_admin];
    if (!clinicianRoles.includes(user.role)) {
      throw new ForbiddenException('Only clinicians can access the triage review queue.');
    }
  }

  private publicCase<T extends { symptomsText: string; questionnaire: unknown }>(
    triageCase: T,
  ): Omit<T, 'symptomsText' | 'questionnaire'> {
    const {
      symptomsText: _symptomsText,
      questionnaire: _questionnaire,
      ...publicTriageCase
    } = triageCase;
    return publicTriageCase;
  }
}
