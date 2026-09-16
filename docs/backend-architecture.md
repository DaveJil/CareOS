# CareOS Backend Architecture

## Phase 0 Stack Decision

CareOS uses a Node.js and TypeScript backend with NestJS. The stack is intentionally conservative: it gives strong module boundaries, first-class OpenAPI support, mature validation middleware, and good test ergonomics while remaining easy to deploy in Nigerian cloud or managed-platform environments.

- Runtime: Node.js 24 LTS-compatible TypeScript service.
- API framework: NestJS with REST controllers and generated OpenAPI documentation.
- Database: PostgreSQL for transactional healthcare, insurance, referral, audit, and payment data.
- ORM and migrations: Prisma with versioned SQL migrations.
- Cache and background jobs: Redis, with BullMQ planned when asynchronous workflows begin.
- Object storage: S3-compatible storage, with MinIO for local development.
- API docs: Swagger UI mounted at `/v1/docs`.
- Testing: Jest for unit and integration tests, Supertest for HTTP endpoint coverage.
- Observability: structured request logging, request IDs, Sentry-ready environment configuration.
- Local infrastructure: Docker Compose for PostgreSQL, Redis, and MinIO.

## Folder Layout

```text
backend/
  src/
    common/
      errors/        Global exception filter and consistent error shape
      logging/       Request ID and request logging middleware
    config/          Environment validation
    modules/
      health/        Health-check endpoint
      rbac/          Roles, permissions, decorators, and guard
    app.module.ts    Root module
    main.ts          HTTP bootstrap, security middleware, OpenAPI
  prisma/
    schema.prisma    Versioned database model source
    migrations/      SQL migrations
  test/              Unit and endpoint tests
```

## Environment Strategy

All configuration comes from environment variables. No secrets are committed. Local development can use `backend/.env.example` as a template. Staging and production must supply separate values through the deployment platform or secrets manager.

Required environment groups:

- Core service: `NODE_ENV`, `PORT`, `SERVICE_NAME`, `API_VERSION`.
- Database: `DATABASE_URL`.
- Cache: `REDIS_URL`.
- Object storage: `OBJECT_STORAGE_ENDPOINT`, `OBJECT_STORAGE_BUCKET`, `OBJECT_STORAGE_REGION`, `OBJECT_STORAGE_ACCESS_KEY`, `OBJECT_STORAGE_SECRET_KEY`.
- Auth secrets: `JWT_ACCESS_TOKEN_SECRET`, `JWT_REFRESH_TOKEN_SECRET`.
- Observability: `SENTRY_DSN`, `LOG_LEVEL`.

## RBAC Model

Phase 0 defines these platform roles:

- `patient`
- `clinician`
- `hospital_admin`
- `hmo_staff`
- `fund_admin`
- `super_admin`

Each protected endpoint must declare required permissions using `RequirePermissions(...)`. The RBAC guard denies protected requests without an authenticated user or without the required permission. Super admins receive all permissions.

## Security Baseline

- Helmet is enabled for HTTP security headers.
- CORS is explicit and credential-aware.
- Global validation strips unknown input and rejects non-whitelisted fields.
- Global errors use a consistent public response shape and do not expose stack traces.
- Request logging uses request IDs and does not log request bodies to avoid PHI leakage.
- Audit table is present from the first migration so clinical, payment, claims, and admin modules can write audit events as they are added.

## Local Development

```bash
cd backend
cp .env.example .env
docker compose up -d
npm install
npm run prisma:deploy
npm run start:dev
```

Health check:

```bash
curl http://localhost:3000/v1/health
```

API docs:

```text
http://localhost:3000/v1/docs
```

## Testing

```bash
cd backend
npm test
npm run lint
npm run build
```

## Staging Deployment

The Phase 0 repository includes staging-ready environment separation and CI checks. A public staging URL is not created from this local workstation; deployment credentials and target hosting must be provided before an externally reachable staging service can be launched.

## Phase 1 Account and Access Additions

Phase 1 adds account registration/login, refresh-token sessions, trusted devices, phone/password-reset OTP storage, and versioned consent history. See [backend-phase1-account-access.md](./backend-phase1-account-access.md) for the endpoint list, data model changes, tests, and open risks.

## MVP Tracking

The current backend implementation is now following the trimmed MVP prompt. See [backend-mvp-status.md](./backend-mvp-status.md) for phase alignment and deviations from the earlier full production prompt.

Phase details:

- [MVP Phase 2 — Patient Profile and Medical Vault](./backend-mvp-phase2-patient-vault.md)
- [MVP Phase 3 — Symptom Scan and AI Triage](./backend-mvp-phase3-triage.md)
- [MVP Phase 4 — Telehealth](./backend-mvp-phase4-telehealth.md)
- [MVP Phase 5 — Payments](./backend-mvp-phase5-payments.md)
- [MVP Phase 6 — Notifications](./backend-mvp-phase6-notifications.md)
- [MVP Phase 7 — Admin and Monitoring](./backend-mvp-phase7-admin.md)
- [MVP Phase 8 — Pilot Readiness](./backend-mvp-phase8-pilot-readiness.md)
