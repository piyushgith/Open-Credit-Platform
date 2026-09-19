# Documents, KYC & Underwriting

## Documents (`document` module)

`LoanDocument` per uploaded document, with its own `DocumentStatus` lifecycle (validated by
`document/support`'s transition guard — `IllegalDocumentTransitionException` on an illegal move).
Verify/reject are separate actions from upload.

```
POST /api/loans/{reference}/documents
GET  /api/loans/{reference}/documents
POST /api/loans/{reference}/documents/{documentId}/verify
POST /api/loans/{reference}/documents/{documentId}/reject
```

## KYC (`kyc` module)

`UNIQUE (application_id)` — one `KycCase` per application (`DuplicateKycCaseException` on a second
attempt). `KycStatus` transitions validated the same way as documents
(`IllegalKycTransitionException`).

```
POST /api/loans/{reference}/kyc
GET  /api/loans/{reference}/kyc
POST /api/loans/{reference}/kyc/verify
POST /api/loans/{reference}/kyc/reject
```

## Underwriting (`underwriting` module)

Owns the `UnderwritingCase`/`UnderwritingAttempt` **audit trail** — not the decisioning logic
itself. `LoanApplicationService.runUnderwriting` (private) orchestrates *which* strategy runs and
what its outcome means for `ApplicationStatus`; `UnderwritingService` only starts and completes
attempts around that run, and emits `UNDERWRITING_STARTED`/`UNDERWRITING_COMPLETED` audit events
(see [ADR-005](../adr/ADR-005-audit-strategy.md)).

**One application can have multiple attempts.** `startAttempt` deactivates (and flushes) any
previously active attempt before inserting the new one, so the insert never collides with the
database's one-active-attempt-per-case partial unique index
(`uq_underwriting_attempt_active_per_case`). Each attempt gets a sequential `cycleNumber`
(`UNIQUE (underwriting_case_id, cycle_number)`). A `REFERRED` outcome leaves the application at
`UNDERWRITING`, retryable via `loan/{reference}/underwriting/retry` — which records a *new* attempt
rather than overwriting the referred one, so referred attempts stay visible in history.

```
GET /api/loans/{reference}/underwriting     the case + every attempt, in cycle order
```

See [loan-lifecycle.md](loan-lifecycle.md) for how an attempt's outcome (`APPROVED`/`DECLINED`/
`REFERRED`) drives `ApplicationStatus`.
