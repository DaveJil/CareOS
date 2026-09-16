# MVP Phase 7 — Admin and Monitoring

## Scope Implemented

- Super-admin patient list.
- Super-admin clinician list.
- Clinician approval.
- High-urgency triage queue.
- Payment summary.
- Audit CSV export.

## Endpoints

All endpoints are under `/v1/admin` and require a super-admin bearer token.

- `GET /admin/patients`
- `GET /admin/clinicians`
- `PATCH /admin/clinicians/:id/approve`
- `GET /admin/triage/high-urgency`
- `GET /admin/payments`
- `GET /admin/audit.csv`

## Audit Coverage

Clinician approval writes an audit record. Earlier phases also write audit entries for triage, vault, profile, notification, booking, and payment activity.

## Tests

Covered by `backend/test/phase4-8.mvp.spec.ts`.
