# Phase 1 — Account and Access

## Implemented API Surface

All endpoints are available under the configured API prefix, currently `/v1`.

### Auth

- `POST /auth/register` — create a patient, clinician, hospital admin, HMO staff, fund admin, or super admin account.
- `POST /auth/login` — verify email/password and issue access/refresh tokens.
- `POST /auth/otp/verify-phone` — verify a phone OTP for phone ownership.
- `POST /auth/refresh` — rotate a refresh token and issue a new token pair.
- `POST /auth/biometric/exchange` — exchange a trusted-device refresh token for a token pair.
- `POST /auth/logout` — revoke a single refresh token.
- `POST /auth/logout-all` — revoke all refresh tokens for the authenticated user.
- `POST /auth/password-reset/request` — create a password reset OTP if the account exists.
- `POST /auth/password-reset/confirm` — verify reset OTP, update password, and revoke active sessions.

### Consent

- `POST /consents` — grant versioned consent for medical records, telehealth, AI triage, or data sharing.
- `GET /consents` — list consent history for the authenticated user.
- `POST /consents/revoke` — revoke the latest active consent of a given type.

### Protected Sample

- `GET /protected/profile` — sample bearer-token and RBAC-protected route requiring `profile:read:own`.

## Security and Audit Behavior

- Passwords are hashed with bcryptjs.
- Access tokens are signed HS256 JWTs using the configured `JWT_ACCESS_TOKEN_SECRET`.
- Refresh tokens are generated as random opaque tokens and stored as SHA-256 hashes.
- OTPs are stored as bcrypt hashes and expire after 10 minutes.
- Trusted devices are recorded by `(userId, deviceId)` for biometric token exchange.
- Registration, login, token refresh, logout-all, password reset, and consent grant/revoke write audit entries.
- Password reset request responses are intentionally non-enumerating: the API returns success even if no account exists.

## Database Changes

The Phase 1 migration adds:

- `passwordHash`, verification flags, and disabled timestamp to `User`.
- `RefreshToken` for session management.
- `TrustedDevice` for biometric exchange.
- `OtpCode` for phone verification and password reset.
- `ConsentRecord` for versioned consent history.
- `OtpPurpose` and `ConsentType` enums.

## Testing

Phase 1 test coverage includes:

- Registration and login for all roles:
  - patient
  - clinician
  - hospital admin
  - HMO staff
  - fund admin
  - super admin
- Duplicate registration rejection.
- Invalid login rejection.
- Consent grant/list/revoke flow with audit entry assertions.
- Existing RBAC allow/deny tests.
- Health endpoint e2e smoke test.

Run:

```bash
cd backend
npm test
npm run lint
npm run build
```

## Open Risks

- Email, SMS, and OTP delivery providers are stubbed at storage level; actual delivery belongs to the notification phase.
- The development OTP is deterministic in this phase to keep tests and early local workflows predictable. Replace with generated OTP delivery when the notification service is implemented.
- External staging deployment remains blocked until hosting credentials and target infrastructure are available.
