# MVP Phase 6 — Notifications

## Scope Implemented

- Notification records for SMS, push, and email.
- Mock dispatcher for pilot/demo delivery.
- Delivery attempt tracking.
- Failure state, last error, and next retry time.
- Retry API for due failed notifications.
- Booking confirmation notification after paid webhook confirmation.

## Endpoints

All endpoints are under `/v1` and require a bearer token.

- `POST /notifications/dispatch`
  - Creates and immediately attempts a notification.

- `POST /notifications/retry-failed`
  - Retries due failed notifications.

## Pilot Boundary

Targets containing `fail` intentionally fail in the mock dispatcher so failed-delivery flows can be tested. Real providers should be added behind `NotificationDispatcher`.

## Tests

Covered by `backend/test/phase4-8.mvp.spec.ts`.
