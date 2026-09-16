# CareOS Production Readiness Inventory

Date: 2026-09-14

This inventory is the release checklist for removing MVP/pilot behavior. Production must not launch until every item is closed, externally verified, or explicitly accepted as hidden from users.

## Demo, Seed, Fixture, and Mock Inventory

| Item | Location | Risk | Production replacement/status |
|---|---|---|---|
| Pilot seed script with demo patient, clinician, admin, profile, and triage case | `backend/prisma/seed.ts` | Could create demo users in production | Guarded: refuses `NODE_ENV=production` and requires `CAREOS_ALLOW_PILOT_SEED=true`. Keep out of production runbooks. |
| Demo account markers | `patient.demo@careos.local`, `clinician.demo@careos.local`, `admin.demo@careos.local` | Demo accounts reachable by real users | `npm run prod:demo-audit` fails if known markers exist in DB. Must run against production before launch. |
| Rule-based AI triage provider | `backend/src/modules/triage/ai-triage.provider.ts` | Not a clinically validated production AI provider | Production config rejects `AI_TRIAGE_PROVIDER=mock`; Gemini adapter is wired for `AI_TRIAGE_PROVIDER=external` with structured JSON output. Live clinical validation still required. |
| Mock video session provider | `backend/src/modules/telehealth/video-session.provider.ts` | No real video/audio, TURN/STUN, call limits, or provider audit | Production config rejects `VIDEO_PROVIDER=mock`; Daily.co adapter is wired for `VIDEO_PROVIDER=external`. Live call test still required. |
| Mock Paystack provider and example checkout URL | `backend/src/modules/payments/payment-gateway.provider.ts` | No real money movement | Production config requires `PAYMENT_PROVIDER=paystack`, `PAYSTACK_SECRET_KEY`, and `PAYSTACK_WEBHOOK_SECRET`. Live small transaction still required. |
| Unsigned payment webhook | `backend/src/modules/payments/*` | Anyone could mark a payment paid | Webhook signature verification added through `PaymentGatewayProvider.verifyWebhookSignature`. Raw-body canonical verification should be validated against Paystack in staging. |
| Mock notification dispatcher | `backend/src/modules/notifications/notification-dispatcher.ts` | No real SMS/push/email delivery | Production config rejects `NOTIFICATION_PROVIDER=mock`; Sendchamp SMS and Resend email adapters are wired for `NOTIFICATION_PROVIDER=external`. Firebase push credentials are still required. |
| Mobile QA checklist | `lib/main.dart` | Debug/test affordance visible to production users | Hidden when `CAREOS_ENV=production`. |
| Mobile test notification sender | `lib/main.dart` | QA-only backend notification creation visible to production users | Hidden when `CAREOS_ENV=production`. |
| HMO, fund, referrals, pharmacy feature placeholders | `lib/main.dart` | Non-integrated partner features could imply live service | Kept as coming-soon/hidden provider states until production partner APIs exist. |
| MVP docs with demo seed instructions | `docs/backend-mvp-*` | Old docs can mislead operators | Retained as history; production launch must use this inventory and production sign-off docs instead. |

## Environment Separation Check

Production readiness now requires explicit production provider variables. The backend refuses to boot in `NODE_ENV=production` with mock providers or missing live credentials.

Required production separation to verify outside this workstation:

- Production PostgreSQL database is separate from local/staging.
- Production object-storage bucket is separate from local/staging.
- Production Redis/cache is separate from local/staging.
- Production AI/video/payment/notification credentials are separate from local/staging.
- Live secrets pasted into chat or logs have been rotated before production use.
- Mobile production builds are created with `--dart-define=CAREOS_ENV=production --dart-define=CAREOS_API_BASE_URL=<production-url>`.

## TODO/Temporary/Hardcoded Findings

- Push notifications still need Firebase server credential wiring.
- Clinician approval exists but credential verification documents/license workflow is not built.
- Account deletion/data-retention workflow is not built.
- File upload malware scanning is not built.
- Rate limiting is not built.
- Load testing, backup restore, monitoring alerts, and rollback tests require deployed infrastructure.
