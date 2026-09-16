# MVP Phase 2 — Patient Profile and Medical Vault

## Endpoints

All endpoints are under `/v1` and require a bearer token.

### Patient Profile

- `GET /patient/profile`
- `PATCH /patient/profile`
- `POST /patient/emergency-contacts`
- `GET /patient/emergency-contacts`
- `DELETE /patient/emergency-contacts/:id`
- `POST /patient/allergies`
- `GET /patient/allergies`
- `DELETE /patient/allergies/:id`
- `POST /patient/chronic-conditions`
- `GET /patient/chronic-conditions`
- `DELETE /patient/chronic-conditions/:id`

### Medical Vault

- `POST /vault/documents`
- `GET /vault/documents`
- `GET /vault/documents/:id`

`POST /vault/documents` accepts a base64 payload for MVP simplicity. The service validates file type and size, encrypts the payload with AES-256-GCM, stores it under local encrypted storage, and persists only metadata plus checksum in PostgreSQL. Supported MIME types:

- `application/pdf`
- `image/jpeg`
- `image/png`
- `image/webp`

Maximum file size: 10 MB.

## Storage Notes

Local encrypted files are stored under `backend/storage/vault/` and ignored by git. The code is isolated behind `EncryptedStorageService` so it can be replaced with S3-compatible object storage without changing the controller/service contract.

## Audit Coverage

Phase 2 writes audit records for:

- profile view
- profile upsert
- emergency contact create/delete
- allergy create/delete
- chronic condition create/delete
- document upload
- document list/search
- document metadata view

## Tests

Covered by `backend/test/phase2.patient-vault.spec.ts`.
