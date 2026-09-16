CREATE TYPE "BloodGroup" AS ENUM (
  'A_positive',
  'A_negative',
  'B_positive',
  'B_negative',
  'AB_positive',
  'AB_negative',
  'O_positive',
  'O_negative',
  'unknown'
);

CREATE TYPE "MedicalDocumentType" AS ENUM (
  'medical_record',
  'prescription',
  'lab_result',
  'imaging',
  'referral',
  'discharge_note',
  'insurance_claim'
);

CREATE TYPE "ScanStatus" AS ENUM (
  'pending',
  'clean',
  'infected',
  'rejected'
);

CREATE TABLE "PatientProfile" (
  "id" TEXT NOT NULL,
  "userId" TEXT NOT NULL,
  "firstName" TEXT NOT NULL,
  "lastName" TEXT NOT NULL,
  "dateOfBirth" TIMESTAMP(3),
  "gender" TEXT,
  "address" TEXT,
  "city" TEXT,
  "state" TEXT,
  "country" TEXT NOT NULL DEFAULT 'NG',
  "bloodGroup" "BloodGroup" NOT NULL DEFAULT 'unknown',
  "genotype" TEXT,
  "nextOfKinName" TEXT,
  "nextOfKinPhone" TEXT,
  "nextOfKinRelationship" TEXT,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "PatientProfile_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "PatientProfile_userId_key" ON "PatientProfile"("userId");

ALTER TABLE "PatientProfile"
  ADD CONSTRAINT "PatientProfile_userId_fkey"
  FOREIGN KEY ("userId") REFERENCES "User"("id")
  ON DELETE CASCADE ON UPDATE CASCADE;

CREATE TABLE "EmergencyContact" (
  "id" TEXT NOT NULL,
  "userId" TEXT NOT NULL,
  "name" TEXT NOT NULL,
  "phone" TEXT NOT NULL,
  "relationship" TEXT NOT NULL,
  "priority" INTEGER NOT NULL DEFAULT 1,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "EmergencyContact_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "EmergencyContact_userId_idx" ON "EmergencyContact"("userId");

ALTER TABLE "EmergencyContact"
  ADD CONSTRAINT "EmergencyContact_userId_fkey"
  FOREIGN KEY ("userId") REFERENCES "User"("id")
  ON DELETE CASCADE ON UPDATE CASCADE;

CREATE TABLE "Allergy" (
  "id" TEXT NOT NULL,
  "userId" TEXT NOT NULL,
  "name" TEXT NOT NULL,
  "severity" TEXT,
  "reaction" TEXT,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "Allergy_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "Allergy_userId_idx" ON "Allergy"("userId");

ALTER TABLE "Allergy"
  ADD CONSTRAINT "Allergy_userId_fkey"
  FOREIGN KEY ("userId") REFERENCES "User"("id")
  ON DELETE CASCADE ON UPDATE CASCADE;

CREATE TABLE "ChronicCondition" (
  "id" TEXT NOT NULL,
  "userId" TEXT NOT NULL,
  "name" TEXT NOT NULL,
  "diagnosedAt" TIMESTAMP(3),
  "notes" TEXT,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "ChronicCondition_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "ChronicCondition_userId_idx" ON "ChronicCondition"("userId");

ALTER TABLE "ChronicCondition"
  ADD CONSTRAINT "ChronicCondition_userId_fkey"
  FOREIGN KEY ("userId") REFERENCES "User"("id")
  ON DELETE CASCADE ON UPDATE CASCADE;

CREATE TABLE "MedicalDocument" (
  "id" TEXT NOT NULL,
  "userId" TEXT NOT NULL,
  "type" "MedicalDocumentType" NOT NULL,
  "title" TEXT NOT NULL,
  "description" TEXT,
  "objectKey" TEXT NOT NULL,
  "originalName" TEXT NOT NULL,
  "mimeType" TEXT NOT NULL,
  "sizeBytes" INTEGER NOT NULL,
  "checksumSha256" TEXT NOT NULL,
  "tags" TEXT[] DEFAULT ARRAY[]::TEXT[],
  "encrypted" BOOLEAN NOT NULL DEFAULT true,
  "scanStatus" "ScanStatus" NOT NULL DEFAULT 'pending',
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "MedicalDocument_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "MedicalDocument_userId_idx" ON "MedicalDocument"("userId");
CREATE INDEX "MedicalDocument_type_idx" ON "MedicalDocument"("type");
CREATE INDEX "MedicalDocument_createdAt_idx" ON "MedicalDocument"("createdAt");

ALTER TABLE "MedicalDocument"
  ADD CONSTRAINT "MedicalDocument_userId_fkey"
  FOREIGN KEY ("userId") REFERENCES "User"("id")
  ON DELETE CASCADE ON UPDATE CASCADE;
