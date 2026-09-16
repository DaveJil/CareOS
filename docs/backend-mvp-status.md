# CareOS Backend MVP Status

This document tracks the backend against the trimmed MVP prompt.

## Already Done

### Phase 0 — Foundation

- NestJS/TypeScript backend under `backend/`.
- PostgreSQL, Redis, and MinIO local infrastructure via Docker Compose.
- Environment validation and separation for local/test/staging/production.
- Global request logging, validation, error response shape, and `/v1/health`.
- Swagger/OpenAPI at `/v1/docs`.
- RBAC guard and tests.
- CI workflow for backend lint/test/build.

Deviation: the first backend pass followed the larger production prompt, so the role enum still includes future roles. MVP endpoints currently use only patient/clinician paths; `super_admin` is reserved for the MVP admin phase.

### Phase 1 — Account and Access

- Patient and clinician registration/login are implemented.
- Phone OTP verification and password reset OTP storage are implemented.
- Refresh-token sessions are implemented.
- Consent records exist.
- A protected sample route verifies bearer token plus RBAC.

Deviation: the earlier production pass also implemented trusted-device/biometric exchange and logout-all. These are not needed for the MVP loop and should be treated as dormant post-MVP surface unless removed later.

### Phase 2 — Patient Profile and Medical Vault

- Patient demographic profile create/update/read.
- Emergency contacts, allergies, and chronic conditions.
- Encrypted document upload from base64 payload.
- Document list/search for the current patient.
- Document metadata read for the current patient.
- Audit entries for profile/document write and document view/list actions.

Out-of-scope items from the full spec were intentionally not continued: timed record sharing, provider record links, and longitudinal referral/HMO/fund timelines.

### Phase 3 — Symptom Scan and AI Triage

- Text and structured-questionnaire symptom intake.
- Pluggable AI triage provider abstraction.
- Mock rule-based AI triage implementation for pilot/demo use.
- Risk scoring with urgency, suggested specialty, and recommended action.
- Per-patient triage history.
- High-urgency clinician review queue.
- Clinician mark-as-reviewed action.
- Audit entries for triage writes and review activity.

Out-of-scope MVP items intentionally skipped: voice input, photo/video symptom capture, emergency responder routing, and full clinician override workflow.

### Phase 4 — Telehealth

- Clinician profile creation/update with approval flag.
- Approved specialist search by specialty and optional slot.
- Patient booking creation with clinician conflict detection.
- Mock video session provisioning for pilot use.
- Booking reschedule, cancel, and participant read.
- Consultation chat messages per booking.

Deviation: video is provider-ready but uses a mock provider. A real provider such as Daily, Twilio, or Agora should replace it before production.

### Phase 5 — Payments

- Idempotent booking charge creation.
- Mock Paystack-style gateway initialization.
- Payment webhook confirmation.
- Paid booking confirmation.
- Receipt response for paid payments.
- Super-admin payment status listing.

Deviation: webhook signature verification is not implemented because the gateway is mocked. Add provider signature verification before using real Paystack/Flutterwave webhooks.

### Phase 6 — Notifications

- SMS/push/email notification records.
- Mock dispatcher.
- Delivery attempt tracking.
- Failed notification retry API.
- Booking confirmation notification after payment success.

Deviation: no real SMS/push provider is connected yet.

### Phase 7 — Admin and Monitoring

- Super-admin patient and clinician lists.
- Clinician approval endpoint.
- High-urgency triage queue.
- Payment summary endpoint.
- Audit CSV export.

### Phase 8 — Pilot Readiness

- Pilot seed script with patient, clinician, and super-admin demo users.
- Phase 4-8 regression test covering the MVP patient/clinician/payment/admin loop.
- Documentation for internal release operation and known mock boundaries.

## Next Backend Work

- Connect real payment webhook signature verification.
- Connect real SMS/push/video providers.
- Add HTTP e2e coverage with a disposable PostgreSQL database.
- Deploy staging with production-like secrets and run migration deploy.
