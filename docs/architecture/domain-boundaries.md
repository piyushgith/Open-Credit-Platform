# Domain / Module Boundaries

One row per module: what it owns, its aggregate root(s), and the invariant the database (not just
Java) enforces.

| Module | Owns (aggregate root in **bold**) | Key invariant, enforced in the DB |
|---|---|---|
| `customer` | **Customer** | `UNIQUE (email)`, `UNIQUE (phone_number)`, `UNIQUE (pan_number)` |
| `loan` | **LoanApplication**, LoanProduct | `ApplicationStatus` transitions validated in `ApplicationLifecycle` before every `setStatus` call — no other module ever calls it |
| `document` | **LoanDocument** | one row per document upload; status transitions validated in `document/exception/IllegalDocumentTransitionException`'s caller |
| `kyc` | **KycCase** | `UNIQUE (application_id)` — one KYC case per application |
| `underwriting` | **UnderwritingCase**, UnderwritingAttempt | `UNIQUE (underwriting_case_id, cycle_number)`; a partial unique index enforces at most one *active* attempt per case |
| `financial` | **FinancialStatement**, FinancialPeriod, FinancialLineItem, FinancialAnalysisRun, DerivedFinancialFact, FinancialRatio, RiskIndicator | `UNIQUE (statement_id, line_item_code)`; derived facts/ratios are always recomputed from persisted raw values, never trusted as client input |
| `scoring` | **Scorecard**, ScorecardRule, Score, RiskFactor | `UNIQUE (analysis_run_id, scorecard_id)`; partial unique index — only one `active` scorecard at a time |
| `decision` | **CreditPolicy**, CreditRule, CreditDecision, RuleResult | `UNIQUE (score_id, policy_id)` — re-deciding the same score under the same policy is rejected, not silently overwritten |
| `authority` | **AuthorityMatrixEntry**, ApprovalCase, ApprovalDecision | `UNIQUE (approval_case_id, role)` — the real guard against the same role (maker or checker) acting twice, even under concurrent requests |
| `offer` | **Offer**, Sanction | partial unique index — at most one `SELECTED` offer per application; `UNIQUE (application_id)` on Sanction |
| `disbursement` | **Disbursement**, DisbursementTranche | `UNIQUE (disbursement_id, request_reference)` — idempotency key against duplicate tranche requests; `Disbursement.version` (`@Version`) protects the running total against a lost update |
| `security` | **AppUser** | `UNIQUE (username)` |
| `audit` | AuditBusinessEvent, AuditDataChange | append-only by construction — `AuditController` exposes no update/delete |
| `common` | cross-cutting: `ApiResponse`/`ApiError` envelope, `GlobalExceptionHandler`, `RequestContext`/`CorrelationIdFilter` | n/a |

## Cross-module dependencies (one-directional, by convention — not yet mechanically enforced)

```
loan       <- offer, disbursement, underwriting, authority (each calls back into loan's service, never its repository, for status changes)
financial  <- decision, authority (read Score/FinancialRatio/RiskIndicator directly via repository)
scoring    <- decision, authority (read Score directly via repository)
decision   <- authority (reads CreditDecision directly via repository)
*          <- audit (every mutating service calls AuditService explicitly)
*          <- security (every controller requires an authenticated principal; approval + admin-config controllers require a specific role)
```

`loan <- authority` is Week 12's addition: `ApprovalService` calls
`LoanApplicationService.applyCreditPipelineOutcome` once Track B's outcome is settled (see
[overview.md](overview.md)'s "Two decision tracks, one lifecycle owner"). `loan` itself still never
imports `decision`/`scoring`/`authority` — the dependency is one-directional, same as every other
row in the diagram above, just newly pointing *into* `loan` from a track that previously called
nothing there. This is enforced only by convention today, not by a `spring-modulith`
`@ApplicationModule` rule. See [ADR-008](../adr/ADR-008-event-driven-evolution.md).

## Aggregate roots and transaction scope

Every aggregate root above is persisted, and its invariant is protected, within a *single*
`@Transactional` method on the module's own service — see
[transaction-boundaries.md](transaction-boundaries.md) for the exact pattern.
