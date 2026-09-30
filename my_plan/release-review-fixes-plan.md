# Release Review Fixes — `developer` vs `main` (2026-09-30)

A gatekeeper review of the whole `developer` branch (the diff against `main` is the entire application)
found gaps where the pipeline could approve credit it shouldn't, and where any logged-in user could move
money. This pass fixes those. It **supersedes** specific decisions in the Week 5, 6, 7, 8, 9 and 10 plans;
each superseded line in those docs points back here. Business-rule choices below were confirmed with the
user before implementation.

## Decisions

| Area | Decision | Supersedes |
|---|---|---|
| Endpoint roles | `MAKER`/`ADMIN`: customer register, loan apply/submit/retry, financial statement submit/analyze, score, decide, open approval case, create/select offer, KYC initiate, document upload. `CHECKER`/`ADMIN`: KYC verify/reject, document verify/reject, sanction, disburse, audit reads. `ADMIN`: the three admin-config controllers (unchanged). GET reads stay any-authenticated. | Week 10 "everything else requires only a valid JWT" |
| ADMIN checker override | Kept as is: ADMIN may act as checker without an `approvalLevel` (documented on `AppRole`, pinned by `adminOverridesInsufficientApprovalLevel`). | — |
| JWT secret | No default in `application.properties`; boot fails without `JWT_SECRET`. The dev secret lives only in `application-dev.properties`. | Week 10 dev-default secret |
| Demo logins | `036` still seeds them; new `043` (context `!demo`) deactivates them. The `dev` profile selects the `demo` Liquibase context. Tests get secret + context from `src/test/resources/config/application.properties`. A DB migrated once without `demo` keeps them inactive. | Week 10 "comment-flagged as demo-only" |
| Missing HARD input | A HARD rule whose input is missing is recorded as a `RuleResult` with `actual_value` NULL, `passed=false`, and forces **REFER** (not DECLINE). Missing SOFT inputs are still skipped. `044` drops NOT NULL on `rule_result.actual_value`. | Week 7 "Missing inputs … skipped entirely" |
| Negative leverage denominators | `DEBT_TO_EQUITY` / `DEBT_TO_EBITDA` are not computed when equity / EBITDA is ≤ 0 (a negative denominator reads as low leverage). The HARD `DEBT_TO_EBITDA` rule then takes the missing-input → REFER path. | Week 5 "Zero denominators" |
| REFER vs AUTO | `ApprovalService.openCase`: a REFER never resolves to AUTO; where the matrix says AUTO it is floored at `SENIOR_CREDIT_MANAGER` (matching `RequiredAuthority.forOutcome`). This hit the seeded policy: its SOFT `CURRENT_RATIO`/`INTEREST_COVERAGE` rules REFER grade-B loans ≤ 500k, which the seeded matrix auto-approved. | Week 8 "a resolved AUTO level means no human sign-off" (for REFER) |
| Already-finalized application | `openCase` returns 409 `ILLEGAL_APPLICATION_TRANSITION` unless the application is DRAFT or UNDERWRITING, so an approval case can't disagree with a strategy-path OFFERED/DECLINED. | — |
| Pipeline approved amount | `computeApprovedTerms` approves the full requested amount, except a product cap the strategy applied (positive amount below the request, e.g. vehicle LTV) still holds. A strategy decline (0) leaves the pipeline as accept authority, unchanged. | — |
| KYC gate | `sanction` requires the application's KYC case `VERIFIED`, else 409 `KYC_NOT_VERIFIED`. Documents are not gated. | Week 9 sanction preconditions |
| Config immutability | Scorecard update and rule add/update/delete, and credit-policy rule add/update/delete, return 409 (`SCORECARD_IN_USE` / `CREDIT_POLICY_IN_USE`) while the config is active or already used by a score/decision — past results stay reproducible. Policy rename stays allowed. Activation requires ≥ 1 rule (`EMPTY_SCORECARD` / `EMPTY_CREDIT_POLICY`). | Week 6/7 admin CRUD |
| Matrix audit | `AuthorityMatrixAdminService` create/update/delete write `audit_data_change` rows (field `entry`, before/after summary). | — |
| Period comparison | `DECLINING_REVENUE`/`DECLINING_EBITDA` compare only against the preceding period of the **same length in months**. | Week 5 "immediately preceding period" |
| Input validation | Request DTOs carry `@Size`/`@Digits` matching their VARCHAR/NUMERIC columns (money: 2 decimals); `existingEmi` and `downPayment` are `@PositiveOrZero`. Customer email is lower-cased; PAN is masked in `CustomerResponse` (`******234F`). | — |
| Error mapping | `GlobalExceptionHandler` maps `DataIntegrityViolationException` / `ObjectOptimisticLockingFailureException` → 409, `MethodArgumentTypeMismatchException` → 400, `NoResourceFoundException` → 404, `HttpRequestMethodNotSupportedException` → 405, instead of 500. | — |
| Login | Unknown/inactive users still pay a BCrypt check (no username timing leak); a token without a `role` claim is rejected. `/swagger-ui.html` is permitted. | — |

## Migrations

```
043-deactivate-demo-users-outside-demo.sql   (context !demo — deactivates the 036 logins)
044-rule-result-actual-value-nullable.sql    (missing HARD input recorded with NULL actual_value)
```

## Tests

Regression tests that fail on the pre-fix code: `DecisionEngineTest` (missing HARD input refers; missing
SOFT input skipped; missing HARD + failed HARD still declines), `RatioCalculatorTest` (negative EBITDA /
equity), `ApprovalWorkflowIntegrationTest` (REFER at 400k grade B opens a SENIOR_CREDIT_MANAGER case;
case on an OFFERED application → 409; used-vehicle LTV cap kept on AUTO approval; checker can't create an
offer), `LoanApplicationIntegrationTest` (sanction without / with rejected KYC → 409),
`ScorecardAdminIntegrationTest` / `CreditPolicyAdminIntegrationTest` (empty activation, editing an
active config), `SecurityIntegrationTest` (maker can't sanction/disburse/read audit; checker can't apply).
MockMvc integration tests now run as `@WithMockUser(roles = "ADMIN")`; concurrency tests pass the context
to worker threads via `DelegatingSecurityContextCallable`. Full suite: 205 tests green.

## Out of scope (open, need a business decision)

- Duplicate-application guard (what counts as a duplicate).
- Underwriting retry cap and a cancel/withdraw state for REFERRED applications stuck at UNDERWRITING.
- Scoring/deciding only ACTUAL/AUDITED statements; one decision per application (latest run only).
- Login rate limiting / lockout.
- `UNIQUE (disbursement_id, tranche_number)` — deliberately not added: `Disbursement.version` already
  rejects the concurrent loser as `CONCURRENT_DISBURSEMENT_CONFLICT`; the constraint would make it fail
  earlier and be mislabeled as a duplicate request.
- `scripts/development/demo.sh` uses GNU `shuf` (not on macOS) — pre-existing.
