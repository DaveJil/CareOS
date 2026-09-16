# CareOS Security Hardening Report

Date: 2026-09-12

## Fixes Applied

- Production backend rejects mock AI/video/payment/notification providers.
- Payment webhook signature verification hook added for Paystack mode.
- Pilot seed script blocked from production and requires explicit opt-in.
- Production demo-marker database audit script added.
- Mobile QA/test notification controls hidden from production.

## Findings Still Open

- Rate limiting is not implemented for login, OTP, password reset, payment initiation, or triage submission.
- Malware scanning is not implemented for vault uploads.
- File upload validation exists for base64/type/size, but production AV scanning is still required.
- Full IDOR review needs endpoint-by-endpoint HTTP tests with multiple users.
- Secret scanning and dependency scanning should be run in CI with tools such as TruffleHog/Gitleaks and npm audit/Snyk.
- Paystack signature verification should be validated with raw webhook payloads in staging.

## IDOR Cases To Run Before Launch

- Patient A cannot read/update Patient B profile.
- Patient A cannot read/delete Patient B emergency contacts, allergies, chronic conditions, or vault documents.
- Patient A cannot view Patient B triage history.
- Patient A cannot view or chat in Patient B booking.
- Clinician A cannot review cases outside authorized queues.
- Non-admin cannot list patients, clinicians, payments, or audit CSV.

## Dependency Scan

Local dependency scan was not completed in this pass. Production launch requires critical/high vulnerabilities to be patched or explicitly accepted by the launch owner.
