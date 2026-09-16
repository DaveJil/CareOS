import { PrismaClient, Role, TriageReviewStatus, TriageUrgency } from '@prisma/client';
import * as bcrypt from 'bcryptjs';

const prisma = new PrismaClient();

async function main() {
  assertSeedAllowed();

  const passwordHash = await bcrypt.hash('CareOS@12345', 12);

  const patient = await prisma.user.upsert({
    where: { email: 'patient.demo@careos.local' },
    update: {},
    create: {
      email: 'patient.demo@careos.local',
      phone: '+2348000000001',
      passwordHash,
      role: Role.patient,
      emailVerified: true,
      phoneVerified: true,
    },
  });

  const clinician = await prisma.user.upsert({
    where: { email: 'clinician.demo@careos.local' },
    update: {},
    create: {
      email: 'clinician.demo@careos.local',
      phone: '+2348000000002',
      passwordHash,
      role: Role.clinician,
      emailVerified: true,
      phoneVerified: true,
    },
  });

  await prisma.user.upsert({
    where: { email: 'admin.demo@careos.local' },
    update: {},
    create: {
      email: 'admin.demo@careos.local',
      phone: '+2348000000003',
      passwordHash,
      role: Role.super_admin,
      emailVerified: true,
      phoneVerified: true,
    },
  });

  await prisma.patientProfile.upsert({
    where: { userId: patient.id },
    update: {},
    create: {
      userId: patient.id,
      firstName: 'Demo',
      lastName: 'Patient',
      city: 'Lagos',
      state: 'Lagos',
      country: 'NG',
      nextOfKinName: 'Demo Kin',
      nextOfKinPhone: '+2348000000099',
      nextOfKinRelationship: 'Sibling',
    },
  });

  await prisma.clinicianProfile.upsert({
    where: { userId: clinician.id },
    update: { approved: true },
    create: {
      userId: clinician.id,
      displayName: 'Dr. Ada Demo',
      specialty: 'General Practice',
      bio: 'Pilot clinician account for internal CareOS testing.',
      priceKobo: 1500000,
      approved: true,
      availability: {
        weekdays: ['monday', 'tuesday', 'wednesday', 'thursday', 'friday'],
        slots: ['09:00', '10:00', '11:00', '14:00'],
      },
    },
  });

  await prisma.triageCase.create({
    data: {
      patientId: patient.id,
      symptomsText: 'Demo patient reports chest tightness and shortness of breath.',
      questionnaire: { painScore: 8, breathingDifficulty: true },
      urgency: TriageUrgency.high,
      suggestedSpecialty: 'Emergency Medicine',
      recommendedAction: 'Seek urgent clinical review.',
      riskScore: 88,
      reviewStatus: TriageReviewStatus.pending,
      aiProvider: 'mock-careos-triage',
      aiRationale: 'Seeded high-urgency case for clinician and admin queue testing.',
    },
  });

  console.log('Seed complete.');
  console.log('Demo password: CareOS@12345');
  console.log('Patient: patient.demo@careos.local');
  console.log('Clinician: clinician.demo@careos.local');
  console.log('Super admin: admin.demo@careos.local');
}

function assertSeedAllowed() {
  if (process.env.NODE_ENV === 'production') {
    throw new Error('Refusing to run pilot seed against NODE_ENV=production.');
  }
  if (process.env.CAREOS_ALLOW_PILOT_SEED !== 'true') {
    throw new Error('Set CAREOS_ALLOW_PILOT_SEED=true to run the pilot seed script.');
  }
}

main()
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
