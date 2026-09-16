import {
  BookingStatus,
  NotificationChannel,
  NotificationStatus,
  Role,
  TriageReviewStatus,
  TriageUrgency,
} from '@prisma/client';
import { AdminService } from '../src/modules/admin/admin.service';
import { NotificationsService } from '../src/modules/notifications/notifications.service';
import { PaymentsService } from '../src/modules/payments/payments.service';
import { TelehealthService } from '../src/modules/telehealth/telehealth.service';
import type { PaymentGatewayProvider } from '../src/modules/payments/payment-gateway.provider';
import type { VideoSessionProvider } from '../src/modules/telehealth/video-session.provider';

describe('Phases 4-8 MVP backend loop', () => {
  it('supports specialist search, booking, chat, payment confirmation, notifications, and admin views', async () => {
    const { admin, notifications, payments, prisma, telehealth } = createHarness();
    const patient = { id: 'patient-1', role: Role.patient };
    const clinician = { id: 'clinician-1', role: Role.clinician };
    const superAdmin = { id: 'admin-1', role: Role.super_admin };

    const profile = await telehealth.upsertClinicianProfile(clinician, {
      displayName: 'Dr. Ada Demo',
      specialty: 'General Practice',
      priceKobo: 1500000,
      approved: true,
      availability: { weekdays: ['monday'], slots: ['09:00'] },
    });
    const specialists = await telehealth.searchSpecialists({ specialty: 'general' });
    const booking = await telehealth.createBooking(patient, {
      clinicianId: clinician.id,
      startsAt: '2026-09-13T09:00:00.000Z',
    });
    await telehealth.sendChatMessage(patient, booking.id, { message: 'Hello doctor' });
    const charge = await payments.createCharge(patient, {
      bookingId: booking.id,
      idempotencyKey: 'booking-1-charge',
    });
    const idempotentCharge = await payments.createCharge(patient, {
      bookingId: booking.id,
      idempotencyKey: 'booking-1-charge',
    });
    const paid = await payments.handleWebhook({
      gateway: 'paystack_mock',
      providerReference: charge.providerReference,
      status: 'success',
      raw: { event: 'charge.success' },
    });
    const receipt = await payments.receipt(patient, paid.id);
    const sentNotification = await notifications.dispatch({
      userId: patient.id,
      channel: NotificationChannel.sms,
      target: '+2348000000001',
      template: 'pilot_welcome',
    });
    const failedNotification = await notifications.dispatch({
      channel: NotificationChannel.sms,
      target: 'fail-target',
      template: 'provider_failure_test',
    });
    const adminPayments = await admin.paymentSummary(superAdmin);
    const auditCsv = await admin.auditCsv(superAdmin);

    expect(profile.approved).toBe(true);
    expect(specialists).toHaveLength(1);
    expect(prisma.bookings[0].status).toBe(BookingStatus.confirmed);
    expect(await telehealth.listChatMessages(clinician, booking.id)).toHaveLength(1);
    expect(idempotentCharge.id).toBe(charge.id);
    expect(receipt.receiptNumber).toMatch(/^CAREOS-/);
    expect(sentNotification.status).toBe(NotificationStatus.sent);
    expect(failedNotification.status).toBe(NotificationStatus.failed);
    expect(adminPayments.totals.paidKobo).toBe(1500000);
    expect(auditCsv).toContain('payment.webhook');
  });

  it('blocks double-booked clinician slots and lets super admins approve clinicians', async () => {
    const { admin, prisma, telehealth } = createHarness();
    const patient = { id: 'patient-1', role: Role.patient };
    const clinician = { id: 'clinician-1', role: Role.clinician };
    const superAdmin = { id: 'admin-1', role: Role.super_admin };

    await telehealth.upsertClinicianProfile(clinician, {
      displayName: 'Dr. Ada Demo',
      specialty: 'Dermatology',
      priceKobo: 1200000,
      approved: false,
      availability: { weekdays: ['monday'], slots: ['09:00'] },
    });
    await admin.approveClinician(superAdmin, clinician.id);
    await telehealth.createBooking(patient, {
      clinicianId: clinician.id,
      startsAt: '2026-09-13T09:00:00.000Z',
    });

    await expect(
      telehealth.createBooking(patient, {
        clinicianId: clinician.id,
        startsAt: '2026-09-13T09:15:00.000Z',
      }),
    ).rejects.toThrow('Clinician is not available');
    expect(prisma.clinicianProfiles[0].approved).toBe(true);
    expect(await admin.highUrgencyQueue(superAdmin)).toHaveLength(1);
  });
});

function createHarness() {
  const prisma = createPrismaMock();
  const audit = {
    record: jest.fn(async (entry: { action: string; target: string }) => {
      await prisma.auditEntry.create({ data: entry });
    }),
  };
  const video = {
    createSession: jest.fn(async ({ bookingId }) => ({
      provider: 'mock-video',
      roomId: `careos-${bookingId}`,
      token: `token-${bookingId}`,
    })),
  } as unknown as VideoSessionProvider;
  const dispatcher = {
    send: jest.fn(async ({ target }) =>
      String(target).includes('fail')
        ? { delivered: false, error: 'Mock failure' }
        : { delivered: true, providerMessageId: 'mock-message' },
    ),
  };
  const gateway = {
    initializeCharge: jest.fn(async ({ bookingId, amountKobo }) => ({
      gateway: 'paystack_mock',
      providerReference: `careos_${bookingId}_${amountKobo}`,
      authorizationUrl: `https://paystack.example.test/pay/careos_${bookingId}_${amountKobo}`,
    })),
    verifyWebhookSignature: jest.fn(() => true),
  } as unknown as PaymentGatewayProvider;
  const notifications = new NotificationsService(
    prisma as never,
    dispatcher as never,
    audit as never,
  );
  const telehealth = new TelehealthService(prisma as never, video, notifications, audit as never);
  const payments = new PaymentsService(prisma as never, gateway, telehealth, audit as never);
  const admin = new AdminService(prisma as never, audit as never);

  return { admin, notifications, payments, prisma, telehealth };
}

function createPrismaMock() {
  const users = [
    {
      id: 'patient-1',
      email: 'patient@careos.test',
      phone: '+2348000000001',
      role: Role.patient,
      status: 'active',
      createdAt: new Date('2026-09-01T00:00:00.000Z'),
      patientProfile: { firstName: 'Demo', lastName: 'Patient' },
    },
    {
      id: 'clinician-1',
      email: 'clinician@careos.test',
      phone: '+2348000000002',
      role: Role.clinician,
      status: 'active',
      createdAt: new Date('2026-09-01T00:00:00.000Z'),
    },
  ];
  const clinicianProfiles: Array<Record<string, unknown>> = [];
  const bookings: Array<Record<string, unknown>> = [];
  const chatMessages: Array<Record<string, unknown>> = [];
  const payments: Array<Record<string, unknown>> = [];
  const notifications: Array<Record<string, unknown>> = [];
  const auditEntries: Array<Record<string, unknown>> = [];
  const triageCases = [
    {
      id: 'triage-1',
      patientId: 'patient-1',
      urgency: TriageUrgency.high,
      reviewStatus: TriageReviewStatus.pending,
      createdAt: new Date('2026-09-01T01:00:00.000Z'),
    },
  ];

  return {
    bookings,
    clinicianProfiles,
    paymentRecords: payments,
    user: {
      findMany: jest.fn(async ({ where }) => users.filter((user) => user.role === where.role)),
    },
    clinicianProfile: {
      upsert: jest.fn(async ({ where, create, update }) => {
        const existing = clinicianProfiles.find((profile) => profile.userId === where.userId);
        if (existing) {
          Object.assign(existing, update, { updatedAt: new Date() });
          return existing;
        }
        const profile = {
          id: `profile-${clinicianProfiles.length + 1}`,
          createdAt: new Date(),
          updatedAt: new Date(),
          user: users.find((user) => user.id === create.userId),
          ...create,
        };
        clinicianProfiles.push(profile);
        return profile;
      }),
      findMany: jest.fn(async ({ where }) =>
        clinicianProfiles
          .filter((profile) => {
            const approved = where.approved === undefined || profile.approved === where.approved;
            const specialty = where.specialty?.contains as string | undefined;
            const matchesSpecialty =
              !specialty ||
              String(profile.specialty).toLowerCase().includes(specialty.toLowerCase());
            return approved && matchesSpecialty;
          })
          .sort((left, right) => String(left.displayName).localeCompare(String(right.displayName))),
      ),
      findUnique: jest.fn(
        async ({ where }) =>
          clinicianProfiles.find((profile) => profile.userId === where.userId) ?? null,
      ),
      update: jest.fn(async ({ where, data }) => {
        const profile = clinicianProfiles.find((candidate) => candidate.userId === where.userId);
        if (!profile) {
          throw new Error('Clinician profile not found');
        }
        Object.assign(profile, data, { updatedAt: new Date() });
        return profile;
      }),
    },
    booking: {
      create: jest.fn(async ({ data }) => {
        const booking = {
          id: `booking-${bookings.length + 1}`,
          status: BookingStatus.pending_payment,
          createdAt: new Date(),
          updatedAt: new Date(),
          ...data,
        };
        bookings.push(booking);
        return booking;
      }),
      update: jest.fn(async ({ where, data }) => {
        const booking = bookings.find((candidate) => candidate.id === where.id);
        if (!booking) {
          throw new Error('Booking not found');
        }
        Object.assign(booking, data, { updatedAt: new Date() });
        return booking;
      }),
      findFirst: jest.fn(async ({ where }) => {
        if (where.OR) {
          return (
            bookings.find(
              (booking) =>
                booking.id === where.id &&
                where.OR.some(
                  (clause: Record<string, string>) =>
                    booking.patientId === clause.patientId ||
                    booking.clinicianId === clause.clinicianId,
                ),
            ) ?? null
          );
        }
        if (where.patientId) {
          const booking =
            bookings.find(
              (candidate) =>
                candidate.id === where.id &&
                candidate.patientId === where.patientId &&
                candidate.status === where.status,
            ) ?? null;
          return booking
            ? {
                ...booking,
                patient: users.find((user) => user.id === booking.patientId),
              }
            : null;
        }
        return (
          bookings.find((booking) => {
            const sameClinician = booking.clinicianId === where.clinicianId;
            const statusMatches = where.status.in.includes(booking.status);
            const excluded = where.id?.not && booking.id === where.id.not;
            const overlaps =
              (booking.startsAt as Date).getTime() < where.startsAt.lt.getTime() &&
              (booking.endsAt as Date).getTime() > where.endsAt.gt.getTime();
            return sameClinician && statusMatches && !excluded && overlaps;
          }) ?? null
        );
      }),
    },
    consultChatMessage: {
      create: jest.fn(async ({ data }) => {
        const message = { id: `chat-${chatMessages.length + 1}`, createdAt: new Date(), ...data };
        chatMessages.push(message);
        return message;
      }),
      findMany: jest.fn(async ({ where }) =>
        chatMessages.filter((message) => message.bookingId === where.bookingId),
      ),
    },
    payment: {
      findUnique: jest.fn(async ({ where }) => {
        if (where.idempotencyKey) {
          return (
            payments.find((payment) => payment.idempotencyKey === where.idempotencyKey) ?? null
          );
        }
        return (
          payments.find((payment) => payment.providerReference === where.providerReference) ?? null
        );
      }),
      create: jest.fn(async ({ data }) => {
        const payment = {
          id: `payment-${payments.length + 1}`,
          currency: 'NGN',
          receiptNumber: null,
          createdAt: new Date(),
          updatedAt: new Date(),
          ...data,
        };
        payments.push(payment);
        return payment;
      }),
      update: jest.fn(async ({ where, data }) => {
        const payment = payments.find((candidate) => candidate.id === where.id);
        if (!payment) {
          throw new Error('Payment not found');
        }
        Object.assign(payment, data, { updatedAt: new Date() });
        return payment;
      }),
      findFirst: jest.fn(
        async ({ where }) =>
          payments.find(
            (payment) =>
              payment.id === where.id &&
              (!where.patientId || payment.patientId === where.patientId),
          ) ?? null,
      ),
      findMany: jest.fn(async () => [...payments].reverse()),
    },
    notification: {
      create: jest.fn(async ({ data }) => {
        const notification = {
          id: `notification-${notifications.length + 1}`,
          status: NotificationStatus.pending,
          attempts: 0,
          createdAt: new Date(),
          updatedAt: new Date(),
          ...data,
        };
        notifications.push(notification);
        return notification;
      }),
      findUnique: jest.fn(
        async ({ where }) =>
          notifications.find((notification) => notification.id === where.id) ?? null,
      ),
      update: jest.fn(async ({ where, data }) => {
        const notification = notifications.find((candidate) => candidate.id === where.id);
        if (!notification) {
          throw new Error('Notification not found');
        }
        const attempts = data.attempts?.increment
          ? Number(notification.attempts) + data.attempts.increment
          : notification.attempts;
        Object.assign(notification, data, { attempts, updatedAt: new Date() });
        return notification;
      }),
      findMany: jest.fn(async ({ where }) =>
        notifications.filter(
          (notification) =>
            notification.status === where.status &&
            (!notification.nextAttemptAt ||
              (notification.nextAttemptAt as Date).getTime() <= where.nextAttemptAt.lte.getTime()),
        ),
      ),
    },
    triageCase: {
      findMany: jest.fn(async ({ where }) =>
        triageCases.filter(
          (triageCase) =>
            triageCase.urgency === where.urgency && triageCase.reviewStatus === where.reviewStatus,
        ),
      ),
    },
    auditEntry: {
      create: jest.fn(async ({ data }) => {
        const entry = {
          id: `audit-${auditEntries.length + 1}`,
          actorId: data.actor?.id,
          actorRole: data.actor?.role,
          action: data.action,
          target: data.target,
          metadata: data.metadata,
          createdAt: new Date(),
        };
        auditEntries.push(entry);
        return entry;
      }),
      findMany: jest.fn(async () => [...auditEntries].reverse()),
    },
  };
}
