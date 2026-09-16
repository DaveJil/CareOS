CREATE TYPE "TriageUrgency" AS ENUM (
  'low',
  'medium',
  'high'
);

CREATE TYPE "TriageReviewStatus" AS ENUM (
  'not_required',
  'pending',
  'reviewed'
);

CREATE TABLE "TriageCase" (
  "id" TEXT NOT NULL,
  "patientId" TEXT NOT NULL,
  "symptomsText" TEXT NOT NULL,
  "questionnaire" JSONB,
  "urgency" "TriageUrgency" NOT NULL,
  "suggestedSpecialty" TEXT NOT NULL,
  "recommendedAction" TEXT NOT NULL,
  "riskScore" INTEGER NOT NULL,
  "reviewStatus" "TriageReviewStatus" NOT NULL DEFAULT 'not_required',
  "reviewedById" TEXT,
  "reviewedAt" TIMESTAMP(3),
  "aiProvider" TEXT NOT NULL,
  "aiRationale" TEXT NOT NULL,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "TriageCase_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "TriageCase_patientId_idx" ON "TriageCase"("patientId");
CREATE INDEX "TriageCase_urgency_idx" ON "TriageCase"("urgency");
CREATE INDEX "TriageCase_reviewStatus_idx" ON "TriageCase"("reviewStatus");
CREATE INDEX "TriageCase_createdAt_idx" ON "TriageCase"("createdAt");

ALTER TABLE "TriageCase"
  ADD CONSTRAINT "TriageCase_patientId_fkey"
  FOREIGN KEY ("patientId") REFERENCES "User"("id")
  ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "TriageCase"
  ADD CONSTRAINT "TriageCase_reviewedById_fkey"
  FOREIGN KEY ("reviewedById") REFERENCES "User"("id")
  ON DELETE SET NULL ON UPDATE CASCADE;
