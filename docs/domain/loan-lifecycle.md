# Loan Lifecycle

Owned by `loan/LoanApplicationService`, the sole writer of `LoanApplication.status`. Every
transition is validated by `loan/support/ApplicationLifecycle.assertTransition` before it's applied
— no other module ever calls `setStatus` directly.

```
DRAFT --submit()--> SUBMITTED --(internal)--> UNDERWRITING --+-- APPROVED --> OFFERED
                                                                +-- DECLINED --> DECLINED
                                                                +-- REFERRED --> (stays UNDERWRITING)

OFFERED --sanction()--> SANCTIONED --disburse() x N tranches--> DISBURSED
```

## `submit()`

Moves `DRAFT -> SUBMITTED -> UNDERWRITING` in one call, then immediately runs underwriting
(`runUnderwriting`, private): starts an `UnderwritingAttempt` (delegated to
[`underwriting`](underwriting.md)), runs the product's `LoanProcessingStrategy` (e.g.
`PersonalLoanStrategy`'s FOIR check), completes the attempt with the outcome, and sets
`LoanApplication.decision`/`decisionDetails`. `APPROVED` advances to `OFFERED`; `DECLINED` advances
to `DECLINED`; `REFERRED` leaves the application at `UNDERWRITING` for `retryUnderwriting()` later.

Each status change is separately audited (`AuditService.recordDataChange("LoanApplication",
id, "status", previous, new)`), and the initial `SUBMITTED` transition also emits an
`APPLICATION_SUBMITTED` business event.

## `sanction()`

Requires the application to already be at `OFFERED` (validated first — preserves "sanction before
submit is illegal" behavior even before delegating). Delegates to
[`offer`](../architecture/domain-boundaries.md)'s `OfferService.sanctionSelectedOffer`, which
requires exactly one `SELECTED` offer. On success, `LoanApplication.status -> SANCTIONED`.

## `disburse()`

Requires `SANCTIONED`. Delegates to `DisbursementService.recordTranche`, which owns the running
total and per-tranche idempotency (`request_reference` uniqueness). `LoanApplication.status ->
DISBURSED` only fires once `DisbursementService` reports the running total has reached the
sanctioned amount — earlier tranches leave the application at `SANCTIONED`.

## What this module does *not* do

`LoanApplicationService`/`ApplicationLifecycle` never read `CreditDecision`, `Score`, or
`ApprovalCase` — those belong to Track B, described in [decisioning.md](decisioning.md). The
dependency runs the other way for status mutation: `authority.ApprovalService` calls
`LoanApplicationService.applyCreditPipelineOutcome` to advance the same state machine described
above once Track B's own outcome is settled, but `loan` itself stays unaware that `authority`
exists — mutation ownership (only `LoanApplicationService` ever calls `setStatus`) is preserved
even though the trigger can now come from either track. See
[../architecture/overview.md](../architecture/overview.md)'s "Two decision tracks, one lifecycle
owner" section.

## Endpoints

```
POST /api/loans/apply
POST /api/loans/{reference}/submit
POST /api/loans/{reference}/underwriting/retry     (only from UNDERWRITING + REFERRED)
GET  /api/loans/{reference}
GET  /api/loans/{reference}/underwriting

POST /api/loans/{reference}/offers                 create an offer (application must be OFFERED + APPROVED)
GET  /api/loans/{reference}/offers
POST /api/loans/{reference}/offers/{offerId}/select

POST /api/loans/{reference}/sanction
GET  /api/loans/{reference}/sanction

POST /api/loans/{reference}/disbursements           record one tranche
GET  /api/loans/{reference}/disbursements
```
