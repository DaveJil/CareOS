CREATE TYPE "Role" AS ENUM (
  'patient',
  'clinician',
  'hospital_admin',
  'hmo_staff',
  'fund_admin',
  'super_admin'
);

CREATE TABLE "User" (
  "id" TEXT NOT NULL,
  "email" TEXT NOT NULL,
  "phone" TEXT,
  "role" "Role" NOT NULL,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "User_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "AuditEntry" (
  "id" TEXT NOT NULL,
  "actorId" TEXT,
  "actorRole" "Role",
  "action" TEXT NOT NULL,
  "target" TEXT NOT NULL,
  "metadata" JSONB,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "AuditEntry_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "User_email_key" ON "User"("email");
CREATE INDEX "AuditEntry_actorId_idx" ON "AuditEntry"("actorId");
CREATE INDEX "AuditEntry_action_idx" ON "AuditEntry"("action");
CREATE INDEX "AuditEntry_target_idx" ON "AuditEntry"("target");
CREATE INDEX "AuditEntry_createdAt_idx" ON "AuditEntry"("createdAt");

ALTER TABLE "AuditEntry"
  ADD CONSTRAINT "AuditEntry_actorId_fkey"
  FOREIGN KEY ("actorId") REFERENCES "User"("id")
  ON DELETE SET NULL ON UPDATE CASCADE;
