# CareOS Functional Requirements

This list covers the application functions needed to turn the rebuilt Flutter screens into a production-ready CareOS app.

## Account and Access

- Register patients, providers, HMO staff, hospital admins, and super admins.
- Sign in with email/password, biometrics, and verified phone OTP.
- Support role-based access control for patient, clinician, hospital, insurance, fund, and admin users.
- Manage password reset, session expiry, trusted devices, and logout on all devices.
- Collect and verify patient consent for medical records, telehealth, AI triage, and data sharing.

## Patient Profile and Medical Vault

- Create and edit patient demographic profile, next of kin, emergency contacts, allergies, and chronic conditions.
- Store medical records, prescriptions, lab results, imaging reports, referrals, discharge notes, and insurance claims.
- Upload, scan, tag, and search clinical documents.
- Share selected records with a provider using timed consent.
- Show longitudinal health timeline across visits, referrals, HMO events, and fund support.

## Symptom Scan and AI Triage

- Capture symptom photos/video with secure upload and basic quality checks.
- Collect symptoms by voice, text, and structured questionnaire.
- Run clinical risk scoring with emergency escalation rules.
- Generate triage summary, recommended next action, urgency, and suggested specialty.
- Route high-risk cases to emergency responders or live clinical review.
- Store triage history and clinician overrides for audit.

## Telehealth and Specialist Network

- Search specialists by specialty, location, availability, HMO coverage, rating, and language.
- Book, reschedule, cancel, and pay for consultations.
- Run secure video/audio consultation with mute, camera, end call, and connection states.
- Support chat, file sharing, prescription generation, lab orders, and referral creation during consultation.
- Maintain doctor profiles, schedules, pricing, credentials, and verification status.

## Referrals and Hospital Network

- Create hospital referrals with urgency, diagnosis notes, files, receiving facility, and transport status.
- Track referral status from created to accepted, in transit, arrived, admitted, transferred, or closed.
- Show referral ledger for hospitals and admins.
- Notify receiving teams and patient contacts.
- Capture bed availability, receiving doctor, care unit, and handover notes.

## HMO and Insurance Portal

- Verify insurance eligibility through QR, member ID, or API lookup.
- Display plan benefits, coverage limits, exclusions, and utilization history.
- Submit pre-authorizations, prescription claims, lab claims, and hospitalization approvals.
- Track approval state, rejection reasons, documents required, and claim settlement.
- Let providers confirm coverage before delivering care.

## Mutual Care Fund

- Show community pool balance, campaigns, individual support requests, and fund usage.
- Create support requests with diagnosis, amount needed, documents, verification, and deadline.
- Accept donations, recurring contributions, corporate sponsorships, and payout approvals.
- Track donor receipts, campaign progress, beneficiary status, and disbursement audit.
- Add fraud checks, manual review, and transparent ledger entries.

## Payments and Billing

- Process card, transfer, wallet, USSD, insurance co-pay, and donation payments.
- Generate invoices, receipts, refunds, and payment reconciliation reports.
- Support consultation fees, medication payments, fund donations, subscriptions, and hospital deposits.
- Connect to Nigerian payment gateways and accounting exports.

## Notifications

- Send push, SMS, email, and in-app notifications.
- Trigger reminders for appointments, medication, lab results, referrals, HMO approvals, and fund milestones.
- Support emergency alerts to care teams and next of kin.
- Let users manage notification preferences and quiet hours.

## Admin and Operations

- Provide admin dashboard for active patients, emergency cases, providers, HMOs, fund activity, and referral exceptions.
- Manage provider verification, hospital onboarding, HMO integrations, user support, and content moderation.
- Review flagged triage cases, fund requests, suspicious claims, and failed payments.
- Export operational reports and audit logs.

## Security, Compliance, and Reliability

- Encrypt data at rest and in transit.
- Maintain audit logs for record access, clinical decisions, payments, claims, and admin actions.
- Implement consent revocation, data retention, backup, and account deletion workflows.
- Add crash reporting, analytics, observability, and uptime monitoring.
- Support offline-safe draft creation for poor-network clinical contexts.

## Backend and Integrations

- Build APIs for auth, profiles, triage, telehealth, provider search, referrals, HMO, fund, payments, notifications, and admin.
- Integrate AI triage provider, video call provider, cloud file storage, payment gateway, SMS/email provider, HMO systems, and hospital systems.
- Add database models, migrations, seed data, test fixtures, and API documentation.
- Provide staging and production environments with secure secrets management.
