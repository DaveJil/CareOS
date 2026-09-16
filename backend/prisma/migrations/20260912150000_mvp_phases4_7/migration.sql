CREATE TYPE "BookingStatus" AS ENUM (
  'pending_payment',
  'confirmed',
  'cancelled',
  'completed'
);

CREATE TYPE "PaymentStatus" AS ENUM (
  'initiated',
  'pending',
  'paid',
  'failed',
  'refunded'
);

CREATE TYPE "NotificationChannel" AS ENUM (
  'sms',
  'push',
  'email'
);

CREATE TYPE "NotificationStatus" AS ENUM (
  'pending',
  'sent',
  'failed'
);

CREATE TABLE "ClinicianProfile" (
  "id" TEXT NOT NULL,
  "userId" TEXT NOT NULL,
  "displayName" TEXT NOT NULL,
  "specialty" TEXT NOT NULL,
  "bio" TEXT,
  "priceKobo" INTEGER NOT NULL,
  "approved" BOOLEAN NOT NULL DEFAULT false,
  "availability" JSONB NOT NULL,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "ClinicianProfile_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "ClinicianProfile_userId_key" ON "ClinicianProfile"("userId");
CREATE INDEX "ClinicianProfile_specialty_idx" ON "ClinicianProfile"("specialty");
CREATE INDEX "ClinicianProfile_approved_idx" ON "ClinicianProfile"("approved");

ALTER TABLE "ClinicianProfile"
  ADD CONSTRAINT "ClinicianProfile_userId_fkey"
  FOREIGN KEY ("userId") REFERENCES "User"("id")
  ON DELETE CASCADE ON UPDATE CASCADE;

CREATE TABLE "Booking" (
  "id" TEXT NOT NULL,
  "patientId" TEXT NOT NULL,
  "clinicianId" TEXT NOT NULL,
  "startsAt" TIMESTAMP(3) NOT NULL,
  "endsAt" TIMESTAMP(3) NOT NULL,
  "status" "BookingStatus" NOT NULL DEFAULT 'pending_payment',
  "priceKobo" INTEGER NOT NULL,
  "videoProvider" TEXT,
  "videoRoomId" TEXT,
  "videoToken" TEXT,
  "cancelReason" TEXT,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "Booking_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "Booking_patientId_idx" ON "Booking"("patientId");
CREATE INDEX "Booking_clinicianId_idx" ON "Booking"("clinicianId");
CREATE INDEX "Booking_startsAt_idx" ON "Booking"("startsAt");
CREATE INDEX "Booking_status_idx" ON "Booking"("status");

ALTER TABLE "Booking"
  ADD CONSTRAINT "Booking_patientId_fkey"
  FOREIGN KEY ("patientId") REFERENCES "User"("id")
  ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "Booking"
  ADD CONSTRAINT "Booking_clinicianId_fkey"
  FOREIGN KEY ("clinicianId") REFERENCES "User"("id")
  ON DELETE CASCADE ON UPDATE CASCADE;

CREATE TABLE "ConsultChatMessage" (
  "id" TEXT NOT NULL,
  "bookingId" TEXT NOT NULL,
  "senderId" TEXT NOT NULL,
  "message" TEXT NOT NULL,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "ConsultChatMessage_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "ConsultChatMessage_bookingId_idx" ON "ConsultChatMessage"("bookingId");
CREATE INDEX "ConsultChatMessage_senderId_idx" ON "ConsultChatMessage"("senderId");
CREATE INDEX "ConsultChatMessage_createdAt_idx" ON "ConsultChatMessage"("createdAt");

ALTER TABLE "ConsultChatMessage"
  ADD CONSTRAINT "ConsultChatMessage_bookingId_fkey"
  FOREIGN KEY ("bookingId") REFERENCES "Booking"("id")
  ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "ConsultChatMessage"
  ADD CONSTRAINT "ConsultChatMessage_senderId_fkey"
  FOREIGN KEY ("senderId") REFERENCES "User"("id")
  ON DELETE CASCADE ON UPDATE CASCADE;

CREATE TABLE "Payment" (
  "id" TEXT NOT NULL,
  "bookingId" TEXT NOT NULL,
  "patientId" TEXT NOT NULL,
  "amountKobo" INTEGER NOT NULL,
  "currency" TEXT NOT NULL DEFAULT 'NGN',
  "status" "PaymentStatus" NOT NULL DEFAULT 'initiated',
  "gateway" TEXT NOT NULL,
  "providerReference" TEXT NOT NULL,
  "idempotencyKey" TEXT NOT NULL,
  "receiptNumber" TEXT,
  "rawWebhook" JSONB,
  "paidAt" TIMESTAMP(3),
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "Payment_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "Payment_providerReference_key" ON "Payment"("providerReference");
CREATE UNIQUE INDEX "Payment_idempotencyKey_key" ON "Payment"("idempotencyKey");
CREATE INDEX "Payment_bookingId_idx" ON "Payment"("bookingId");
CREATE INDEX "Payment_patientId_idx" ON "Payment"("patientId");
CREATE INDEX "Payment_status_idx" ON "Payment"("status");
CREATE INDEX "Payment_createdAt_idx" ON "Payment"("createdAt");

ALTER TABLE "Payment"
  ADD CONSTRAINT "Payment_bookingId_fkey"
  FOREIGN KEY ("bookingId") REFERENCES "Booking"("id")
  ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "Payment"
  ADD CONSTRAINT "Payment_patientId_fkey"
  FOREIGN KEY ("patientId") REFERENCES "User"("id")
  ON DELETE CASCADE ON UPDATE CASCADE;

CREATE TABLE "Notification" (
  "id" TEXT NOT NULL,
  "userId" TEXT,
  "channel" "NotificationChannel" NOT NULL,
  "target" TEXT NOT NULL,
  "template" TEXT NOT NULL,
  "payload" JSONB,
  "status" "NotificationStatus" NOT NULL DEFAULT 'pending',
  "attempts" INTEGER NOT NULL DEFAULT 0,
  "lastError" TEXT,
  "nextAttemptAt" TIMESTAMP(3),
  "sentAt" TIMESTAMP(3),
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "Notification_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "Notification_userId_idx" ON "Notification"("userId");
CREATE INDEX "Notification_status_idx" ON "Notification"("status");
CREATE INDEX "Notification_channel_idx" ON "Notification"("channel");
CREATE INDEX "Notification_createdAt_idx" ON "Notification"("createdAt");

ALTER TABLE "Notification"
  ADD CONSTRAINT "Notification_userId_fkey"
  FOREIGN KEY ("userId") REFERENCES "User"("id")
  ON DELETE SET NULL ON UPDATE CASCADE;
