import { ForbiddenException } from '@nestjs/common';
import { Role, TriageReviewStatus, TriageUrgency } from '@prisma/client';
import { MockAiTriageProvider } from '../src/modules/triage/ai-triage.provider';
import { TriageService } from '../src/modules/triage/triage.service';

describe('Phase 3 MockAiTriageProvider', () => {
  it('scores high-risk symptoms as high urgency', async () => {
    const provider = new MockAiTriageProvider();

    const result = await provider.assess({
      symptomsText: 'I have chest pain and difficulty breathing',
      questionnaire: { painScore: 9, breathingDifficulty: true },
    });

    expect(result.urgency).toBe(TriageUrgency.high);
    expect(result.suggestedSpecialty).toBe('Emergency Medicine');
    expect(result.riskScore).toBeGreaterThanOrEqual(70);
  });

  it('scores mild symptoms as low urgency', async () => {
    const provider = new MockAiTriageProvider();

    const result = await provider.assess({
      symptomsText: 'I have a mild runny nose today',
      questionnaire: { painScore: 1 },
    });

    expect(result.urgency).toBe(TriageUrgency.low);
  });
});

describe('Phase 3 TriageService', () => {
  it('lets patients submit triage and stores high-urgency cases for review', async () => {
    const { triage, audit, prisma } = createHarness();
    const patient = { id: 'patient-1', role: Role.patient };

    const result = await triage.submit(patient, {
      symptomsText: 'I have chest pain and difficulty breathing',
      questionnaire: { painScore: 9, breathingDifficulty: true },
    });

    expect(result.urgency).toBe(TriageUrgency.high);
    expect(result.reviewStatus).toBe(TriageReviewStatus.pending);
    expect('symptomsText' in result).toBe(false);
    expect(prisma.triageCases).toHaveLength(1);
    expect(audit.records.map((record) => record.action)).toEqual(['triage.submit']);
  });

  it('lets patients view only their own triage history', async () => {
    const { triage } = createHarness();
    const patient = { id: 'patient-1', role: Role.patient };
    await triage.submit(patient, {
      symptomsText: 'Fever, headache, vomiting, and weakness since yesterday',
      questionnaire: { painScore: 6, temperatureCelsius: 38.5 },
    });

    const history = await triage.history(patient, { query: 'fever' });

    expect(history).toHaveLength(1);
    expect(history[0].urgency).toBe(TriageUrgency.medium);
  });

  it('exposes pending high-urgency queue to clinicians and marks cases reviewed', async () => {
    const { triage } = createHarness();
    const patient = { id: 'patient-1', role: Role.patient };
    const clinician = { id: 'clinician-1', role: Role.clinician };
    const submitted = await triage.submit(patient, {
      symptomsText: 'Severe chest pain and shortness of breath',
      questionnaire: { painScore: 10 },
    });

    const queue = await triage.clinicianQueue(clinician);
    const reviewed = await triage.markReviewed(clinician, submitted.id);

    expect(queue).toHaveLength(1);
    expect(reviewed.reviewStatus).toBe(TriageReviewStatus.reviewed);
    expect(reviewed.reviewedById).toBe('clinician-1');
  });

  it('blocks non-patients from submitting and non-clinicians from queue access', async () => {
    const { triage } = createHarness();
    const clinician = { id: 'clinician-1', role: Role.clinician };
    const patient = { id: 'patient-1', role: Role.patient };

    await expect(
      triage.submit(clinician, {
        symptomsText: 'I have headache and fever',
      }),
    ).rejects.toThrow(ForbiddenException);
    await expect(triage.clinicianQueue(patient)).rejects.toThrow(ForbiddenException);
  });
});

function createHarness() {
  const prisma = createPrismaMock();
  const audit = {
    records: [] as Array<{ action: string; target: string }>,
    record: jest.fn(async (entry: { action: string; target: string }) => {
      audit.records.push(entry);
    }),
  };
  return {
    prisma,
    audit,
    triage: new TriageService(prisma as never, new MockAiTriageProvider(), audit as never),
  };
}

function createPrismaMock() {
  const triageCases: Array<Record<string, unknown>> = [];
  return {
    triageCases,
    triageCase: {
      create: jest.fn(async ({ data }) => {
        const triageCase = {
          id: `triage-${triageCases.length + 1}`,
          reviewedById: null,
          reviewedAt: null,
          createdAt: new Date(),
          updatedAt: new Date(),
          ...data,
        };
        triageCases.push(triageCase);
        return triageCase;
      }),
      findMany: jest.fn(async ({ where }) => {
        const cases = triageCases.filter((triageCase) => {
          const matchesPatient = !where.patientId || triageCase.patientId === where.patientId;
          const matchesReview =
            !where.reviewStatus || triageCase.reviewStatus === where.reviewStatus;
          const matchesUrgency =
            !where.urgency?.in || where.urgency.in.includes(triageCase.urgency);
          const query = where.symptomsText?.contains as string | undefined;
          const matchesQuery =
            !query || String(triageCase.symptomsText).toLowerCase().includes(query.toLowerCase());
          return matchesPatient && matchesReview && matchesUrgency && matchesQuery;
        });
        return [...cases].sort(
          (left, right) => (left.createdAt as Date).getTime() - (right.createdAt as Date).getTime(),
        );
      }),
      findFirst: jest.fn(
        async ({ where }) =>
          triageCases.find(
            (triageCase) =>
              triageCase.id === where.id && triageCase.reviewStatus === where.reviewStatus,
          ) ?? null,
      ),
      update: jest.fn(async ({ where, data }) => {
        const triageCase = triageCases.find((candidate) => candidate.id === where.id);
        if (!triageCase) {
          throw new Error('Triage case not found');
        }
        Object.assign(triageCase, data, { updatedAt: new Date() });
        return triageCase;
      }),
    },
  };
}
