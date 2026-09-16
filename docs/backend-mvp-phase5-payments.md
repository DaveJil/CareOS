# MVP Phase 5 — Payments

## Scope Implemented

- Patient payment initialization for pending bookings.
- Idempotent charge creation through `idempotencyKey`.
- Mock Paystack-style provider reference and authorization URL.
- Payment webhook handler.
- Booking confirmation after successful payment webhook.
- Receipt response for paid payments.
- Super-admin payment status listing.

## Endpoints

All endpoints are under `/v1`.

- `POST /payments/charges`
  - Patient creates or retrieves an idempotent charge for a pending booking.

- `POST /payments/webhook`
  - Mock provider webhook.
  - Public route by design so a real gateway can call it.

- `GET /payments/admin/status`
  - Super admin lists recent payment statuses.

- `GET /payments/:id/receipt`
  - Patient or admin retrieves a receipt after payment is paid.

## Pilot Boundary

The payment provider is `MockPaystackGatewayProvider`. Before live internal testing with real money, add provider signature verification, event replay protection, provider transaction verification, and real callback URLs.

## Tests

Covered by `backend/test/phase4-8.mvp.spec.ts`.
