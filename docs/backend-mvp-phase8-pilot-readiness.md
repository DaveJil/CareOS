# MVP Phase 8 — Pilot Readiness

## What Is Ready

- Backend modules for the core MVP loop are wired into `AppModule`.
- Prisma migration `20260912150000_mvp_phases4_7` adds telehealth, payment, notification, and clinician profile tables.
- Pilot seed script creates demo users and starter data.
- Phase 4-8 regression test covers the core patient/clinician/payment/admin journey.
- OpenAPI picks up the new controllers at `/v1/docs`.

## Demo Seed

From `backend/`:

```bash
npm run prisma:deploy
npm run prisma:seed
```

Demo password for all seed accounts:

```text
CareOS@12345
```

Seed accounts:

- Patient: `patient.demo@careos.local`
- Clinician: `clinician.demo@careos.local`
- Super admin: `admin.demo@careos.local`

Seed data:

- Patient profile for the demo patient.
- Approved General Practice clinician profile.
- High-urgency triage case for queue testing.

## Internal Testing Checklist

1. Register/login as a patient.
2. Complete patient profile and consent.
3. Submit triage symptoms.
4. Search approved specialists.
5. Create a telehealth booking.
6. Create a payment charge.
7. Send mock payment webhook with `status: "success"`.
8. Confirm booking status is `confirmed`.
9. Confirm booking notification was recorded.
10. Use the super-admin account to review patients, clinicians, triage, payments, and audit CSV.

## Verification

Commands run locally:

```bash
npm test
npm run lint
npm run build
```

Latest result:

- 6 test suites passed.
- 32 tests passed.
- Lint passed with zero warnings.
- Production build passed.

## Known Pilot Boundaries

- Video provider is mocked.
- Payment provider is mocked.
- Notification provider is mocked.
- Webhook signature verification must be added before real payment providers are enabled.
- HTTP e2e coverage still needs a disposable PostgreSQL test database.
