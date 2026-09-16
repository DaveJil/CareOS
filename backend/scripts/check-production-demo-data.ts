import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

async function main() {
  const findings: string[] = [];

  const demoUsers = await prisma.user.findMany({
    where: {
      OR: [
        { email: { contains: '.demo@' } },
        { email: { endsWith: '@careos.local' } },
        { email: { endsWith: '@careos.test' } },
        { phone: { startsWith: '+234800000000' } },
      ],
    },
    select: { id: true, email: true, phone: true },
    take: 50,
  });
  if (demoUsers.length > 0) {
    findings.push(`demo/test users found: ${demoUsers.map((user) => user.email).join(', ')}`);
  }

  const demoProfiles = await prisma.clinicianProfile.findMany({
    where: {
      OR: [
        { displayName: { contains: 'Demo', mode: 'insensitive' } },
        { bio: { contains: 'pilot', mode: 'insensitive' } },
        { bio: { contains: 'testing', mode: 'insensitive' } },
      ],
    },
    select: { id: true, displayName: true },
    take: 50,
  });
  if (demoProfiles.length > 0) {
    findings.push(
      `demo clinician profiles found: ${demoProfiles.map((profile) => profile.displayName).join(', ')}`,
    );
  }

  const mockTriage = await prisma.triageCase.findMany({
    where: {
      OR: [
        { aiProvider: { contains: 'mock', mode: 'insensitive' } },
        { symptomsText: { contains: 'Demo patient', mode: 'insensitive' } },
        { aiRationale: { contains: 'Seeded', mode: 'insensitive' } },
      ],
    },
    select: { id: true, aiProvider: true },
    take: 50,
  });
  if (mockTriage.length > 0) {
    findings.push(
      `mock/seed triage cases found: ${mockTriage.map((triage) => `${triage.id}:${triage.aiProvider}`).join(', ')}`,
    );
  }

  const mockPayments = await prisma.payment.findMany({
    where: {
      OR: [
        { gateway: { contains: 'mock', mode: 'insensitive' } },
        { providerReference: { contains: 'careos_booking_123', mode: 'insensitive' } },
      ],
    },
    select: { id: true, gateway: true },
    take: 50,
  });
  if (mockPayments.length > 0) {
    findings.push(
      `mock payments found: ${mockPayments.map((payment) => `${payment.id}:${payment.gateway}`).join(', ')}`,
    );
  }

  if (findings.length > 0) {
    console.error('Production demo-data audit failed.');
    for (const finding of findings) {
      console.error(`- ${finding}`);
    }
    process.exitCode = 1;
    return;
  }

  console.log('Production demo-data audit passed: no known demo markers found.');
}

main()
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
