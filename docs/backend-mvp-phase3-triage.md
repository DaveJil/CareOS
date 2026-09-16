# MVP Phase 3 — Symptom Scan and AI Triage

## Scope Implemented

This phase implements the trimmed MVP triage loop:

- Text symptom intake.
- Structured questionnaire intake.
- Pluggable AI triage provider interface.
- Mock rule-based provider for pilot/demo use.
- Risk scoring with urgency, suggested specialty, and recommended action.
- Per-patient triage history.
- High-urgency clinician review queue.
- Clinician mark-as-reviewed action.

Out of scope by MVP prompt and intentionally skipped:

- Voice input.
- Photo/video symptom capture.
- Emergency responder routing.
- Full clinician override workflow.

## Endpoints

All endpoints are under `/v1` and require a bearer token.

### Patient

- `POST /triage`
  - Submit `symptomsText` and optional structured `questionnaire`.
  - Returns urgency, risk score, suggested specialty, recommended action, review status, and AI rationale metadata.

- `GET /triage/history`
  - Returns the current patient's triage history.
  - Optional filters:
    - `query`
    - `urgency`

### Clinician

- `GET /triage/review-queue`
  - Returns high-urgency cases awaiting clinician review.

- `PATCH /triage/:id/reviewed`
  - Marks a pending high-urgency triage case as reviewed.

## Risk Scoring

The MVP provider is `mock-rule-triage-v1`. It is deterministic by design so pilots and tests are predictable.

High-risk signals include terms such as:

- chest pain
- difficulty breathing
- shortness of breath
- seizure
- unconscious
- severe bleeding
- stroke
- suicide

Moderate-risk signals include terms such as:

- fever
- vomiting
- weakness
- dizziness
- abdominal pain
- headache
- infection

Questionnaire fields currently used:

- `painScore`
- `temperatureCelsius`
- `breathingDifficulty`

Urgency thresholds:

- `high`: risk score >= 70
- `medium`: risk score >= 30
- `low`: risk score < 30

High-urgency cases are automatically set to review status `pending`.

## Audit Coverage

Phase 3 writes audit records for:

- triage submission
- triage history view
- clinician review queue view
- case marked reviewed

## Tests

Covered by `backend/test/phase3.triage.spec.ts`.
