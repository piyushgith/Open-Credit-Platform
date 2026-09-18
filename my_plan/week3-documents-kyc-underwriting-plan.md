# Week 3 Implementation Plan — Documents + KYC + Underwriting

## Context

Week 2 (`my_plan/week2-customer-loan-application-plan.md`) shipped the `Customer`/`LoanProduct`/`LoanApplication`
lifecycle with `submit` running decisioning synchronously and inline. Per [my_docs/plan.md](../my_docs/plan.md)
Week 3, this stage adds:

1. `LoanDocument` — document metadata attached to an application.
2. `KycCase` — one KYC case per application.
3. `UnderwritingCase` + `UnderwritingAttempt` — a proper audit trail of every underwriting run, since
   `REFERRED` decisions today park the application at `UNDERWRITING` with no way to re-run it.

## Design decisions

| Decision | Choice |
|---|---|
| Document storage | Metadata only (`documentType`, `fileName`, `storageReference`) — no blob storage exists yet, so `storageReference` is caller-supplied (e.g. a URL/path), not a multipart upload |
| KYC/Underwriting keying | Both are 1:1 with `LoanApplication` (`application_id UNIQUE`); documents are 1:N |
| Underwriting integration | `LoanApplicationService.submit()` now records every decisioning run as an `UnderwritingAttempt` instead of only mutating `LoanApplication` directly. A new `POST /{reference}/underwriting/retry` re-runs decisioning (creating attempt #2, #3, ...) when an application is parked at `UNDERWRITING` with a `REFERRED` outcome — the only way multiple attempts arise today |
| Active-attempt constraint | Enforced twice: service deactivates the previous attempt (flushed) before inserting the new one, and a Postgres partial unique index (`WHERE active`) catches any race |
| Cycle numbers | `count(existing attempts) + 1`, unique per case via `(underwriting_case_id, cycle_number)` |
| Attempt lifecycle | New pure `UnderwritingAttemptLifecycle` guard (mirrors `ApplicationLifecycle`): `IN_PROGRESS -> {APPROVED, DECLINED, REFERRED}`, all three terminal — so a completed attempt can never be overwritten, only superseded by a new row |

## Schema changes (Liquibase, additive changesets)

- `006-create-kyc-case.sql`, `007-create-loan-document.sql`, `008-create-underwriting-case.sql`,
  `009-create-underwriting-attempt.sql`.

## New modules

- `document`: `LoanDocument` + `LoanDocumentService` + `LoanDocumentController`
  (`POST/GET /api/loans/{reference}/documents`, `POST .../{documentId}/verify|reject`).
- `kyc`: `KycCase` + `KycService` + `KycController`
  (`POST/GET /api/loans/{reference}/kyc`, `POST .../verify|reject`).
- `underwriting`: `UnderwritingCase`/`UnderwritingAttempt` + `UnderwritingService` + `UnderwritingController`
  (`GET /api/loans/{reference}/underwriting` — case + full attempt history, read-only).

## Loan lifecycle changes

- `LoanController` gains `POST /{reference}/underwriting/retry`.
- `LoanApplicationService.submit()`'s decisioning block is factored into a private `runUnderwriting` helper
  (shared with the new `retryUnderwriting`) that starts/completes an `UnderwritingAttempt` around the existing
  `LoanProcessingStrategy` call. No change to `apply`/`sanction`/`disburse` or existing response shapes.

## Exception handling additions

`LoanDocumentNotFoundException`/`IllegalDocumentTransitionException`, `KycCaseNotFoundException`/
`DuplicateKycCaseException`/`IllegalKycTransitionException`, `UnderwritingCaseNotFoundException`/
`InvalidUnderwritingStateException`/`UnderwritingNotRetryableException`/
`IllegalUnderwritingAttemptTransitionException`.

## Tests

- `UnderwritingAttemptLifecycleTest` — legal/illegal transitions, terminal states.
- `LoanDocumentServiceTest`, `KycServiceTest`, `UnderwritingServiceTest` — happy paths + conflicts/not-found.
- Extend `LoanApplicationIntegrationTest` (or a new `UnderwritingIntegrationTest`) with a `REFERRED` personal
  loan (FOIR between 0.50 and 0.60) that retries underwriting and asserts cycle 1 becomes inactive while
  cycle 2 is active, and that a second retry on a resolved (non-`REFERRED`) application is rejected.

## Out of scope (later weeks)

Authority matrix / maker-checker for `REFERRED` applications (Week 8), offer/sanction document generation
(Week 9), actual document file storage/virus scanning (never explicitly scheduled — revisit if needed).
