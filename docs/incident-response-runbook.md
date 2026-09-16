# CareOS Incident Response Runbook

## Trigger

Use this runbook for suspected unauthorized access, data leakage, payment compromise, provider breach, or production system compromise.

## Immediate Actions

1. Assign an incident lead and timestamp the start of the incident.
2. Preserve logs and audit records. Do not delete or rotate evidence manually.
3. Revoke or rotate suspected compromised credentials.
4. Disable affected integration keys or endpoints if continued use increases risk.
5. Determine affected users, records, payment objects, and time window.

## Containment

- Backend: disable affected routes through deployment config or rollback.
- Mobile: disable exposed feature flags or ship an emergency build if needed.
- Payments: contact payment provider, freeze suspicious transactions, and verify webhook logs.
- Notifications: pause sender credentials if message content or tokens are exposed.

## Assessment

Document:

- What happened.
- When it started and ended.
- Which systems and data categories were affected.
- Whether PHI, PII, payment data, credentials, or access tokens were exposed.
- Whether data was viewed, altered, deleted, or exfiltrated.

## Notification

Legal/compliance owner must decide user/regulator notification obligations under applicable Nigerian data-protection requirements and partner contracts.

## Recovery

1. Patch or configure the fix.
2. Re-run affected security tests.
3. Verify audit logs and monitoring.
4. Restore from backups only after confirming the restore point is clean.
5. Record final timeline and corrective actions.

## Post-Incident

- Hold a review within five business days.
- Add preventive tests or monitoring.
- Rotate any shared credentials involved.
- Update this runbook if the response exposed gaps.
