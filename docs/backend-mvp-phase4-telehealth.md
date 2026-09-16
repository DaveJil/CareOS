# MVP Phase 4 — Telehealth

## Scope Implemented

- Clinician profile create/update.
- Approved specialist search by specialty.
- Optional slot filtering that hides clinicians with conflicting bookings.
- Patient booking creation.
- Mock video room/token creation.
- Booking read, reschedule, and cancel.
- Consultation chat messages for booking participants.

## Endpoints

All endpoints are under `/v1`.

- `PATCH /telehealth/clinician-profile`
  - Clinician or super admin creates/updates a clinician profile.

- `GET /telehealth/specialists`
  - Search approved specialists.
  - Optional query: `specialty`, `startsAt`.

- `POST /telehealth/bookings`
  - Patient creates a pending-payment booking.

- `GET /telehealth/bookings/:id`
  - Patient or clinician reads their booking.

- `PATCH /telehealth/bookings/:id/reschedule`
  - Participant reschedules if the clinician slot is free.

- `PATCH /telehealth/bookings/:id/cancel`
  - Participant cancels a booking with optional reason.

- `POST /telehealth/bookings/:id/chat`
  - Participant sends a consultation chat message.

- `GET /telehealth/bookings/:id/chat`
  - Participant lists consultation chat messages.

## Pilot Boundary

Video sessions use `MockVideoSessionProvider`. The data model and service interface are ready for a real provider, but pilot builds should treat generated room IDs and tokens as demo-only.

## Tests

Covered by `backend/test/phase4-8.mvp.spec.ts`.
