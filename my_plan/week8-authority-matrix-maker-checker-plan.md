# Week 8 — Authority Matrix + Maker-Checker

## Context

Week 7's `RequiredAuthority` enum (`decision/model/RequiredAuthority.java`) is explicitly documented as
"a fixed, documented placeholder ... not the real `AuthorityMatrix` ... which is Week 8's job." This
week builds that real, configurable matrix and the two-person maker-checker workflow that consumes it.
`RequiredAuthority`/`CreditDecision.requiredAuthority` are left untouched (still driven by outcome +
risk grade, per Week 7) — the new `AuthorityMatrix` is a second, independent, more granular gate
(amount + risk grade + product) that decides whether a `CreditDecision` needs a human approval case at
all, and if so, at what level.

No security/user module exists yet (that's Week 10). Maker/checker identity is therefore carried in the
request as `actorUsername` + `actorLevel` (the level the caller asserts for that actor) rather than
resolved from a JWT/roles table. This is the same "explicit today, secured later" seam Week 7 used for
`RequiredAuthority` itself.

## Build

New package `com.opencredit.platform.authority`:

```
model/      ApprovalLevel, AuthorityMatrixEntry, ApprovalCase, ApprovalCaseStatus,
            ApprovalDecision, ApprovalRole, ApprovalOutcome
repository/ AuthorityMatrixEntryRepository, ApprovalCaseRepository, ApprovalDecisionRepository
support/    AuthorityMatrixResolver (pure), ApprovalLifecycle (pure)
exception/  AuthorityMatrixEntryNotFoundException, NoMatchingAuthorityMatrixEntryException,
            ApprovalCaseNotFoundException, DuplicateApprovalCaseException,
            ApprovalNotRequiredException, IllegalApprovalTransitionException,
            SelfApprovalException, InsufficientApprovalAuthorityException,
            ConcurrentApprovalConflictException
dto/        AuthorityMatrixEntryRequest/Response, ApprovalActionRequest,
            ApprovalCaseResponse, ApprovalDecisionResponse
            AuthorityMatrixAdminController/Service, ApprovalService,
            CreditDecisionApprovalController, ApprovalCaseController
```

Plus one small addition to the existing `decision` module: `CreditDecisionNotFoundException`, needed
because opening an approval case looks up a `CreditDecision` directly by id for the first time.

## Domain model

**`ApprovalLevel`** — `AUTO < CREDIT_OFFICER < SENIOR_CREDIT_MANAGER`, ordinal = seniority, mirroring
`RequiredAuthority`'s three values.

**`AuthorityMatrixEntry`** — one configurable row: optional `productType`, optional `riskGrade`,
optional `minAmount`/`maxAmount` (inclusive; null = unbounded), `requiredLevel`, `matchOrder`
(ascending priority), `active`. Admin CRUD (`AuthorityMatrixAdminController`), no in-use guard on
delete — a case snapshots its resolved level at open time, so entries carry no FK from cases.

**`AuthorityMatrixResolver`** (pure, unit-testable, mirrors `DecisionEngine`) — given active entries
sorted by `matchOrder`, returns the first whose optional criteria all match `(productType, riskGrade,
amount)`. Seeded with a catch-all (`SENIOR_CREDIT_MANAGER`, no criteria, highest `matchOrder`) so
resolution never fails to match; `NoMatchingAuthorityMatrixEntryException` exists only as a safety net
if the catch-all is ever deleted.

**`ApprovalCase`** — one per `CreditDecision` (`decision_id UNIQUE`), `requiredLevel` (resolved once at
open time), `status` (`PENDING_MAKER -> PENDING_CHECKER -> APPROVED|REJECTED`, all reached via
`ApprovalDecision` rows), `@Version` for optimistic locking on the status column.

**`ApprovalDecision`** — append-only row per case per role (`MAKER`/`CHECKER`): `actorUsername`,
`actorLevel`, `outcome` (`APPROVE`/`REJECT`), optional `comment`, `decidedAt`. `UNIQUE
(approval_case_id, role)` at the DB level.

**`ApprovalLifecycle`** (pure, mirrors `UnderwritingAttemptLifecycle`) — guards
`PENDING_MAKER -> PENDING_CHECKER` (maker acts) and `PENDING_CHECKER -> {APPROVED, REJECTED}` (checker
acts); every other transition throws `IllegalApprovalTransitionException`. The checker's outcome is
final regardless of the maker's recommendation — both people always sign off, so a maker's `REJECT`
still goes to a checker rather than short-circuiting, giving symmetric two-person control.

## Business rules -> mechanism

1. **Maker cannot approve their own decision** — checker action compares `actorUsername` against the
   case's `MAKER` `ApprovalDecision` row; equal usernames -> `SelfApprovalException`.
2. **Checker must have sufficient authority** — `checkerLevel.ordinal() >= case.requiredLevel.ordinal()`,
   else `InsufficientApprovalAuthorityException`. (Only checker is gated, per the roadmap's wording —
   the maker is a proposer, not the authority holder.)
3. **Approval authority depends on configured criteria** — `AuthorityMatrixResolver` against the loan's
   `productType`/`requestedAmount` (via `CreditDecision.scoreId -> Score.analysisRunId ->
   FinancialAnalysisRun.statementId -> FinancialStatement.applicationId -> LoanApplication`) and the
   score's `riskGrade`. A `DECLINE` outcome or a resolved `AUTO` level both mean "no human sign-off
   needed" -> `ApprovalNotRequiredException` rather than opening a case.
4. **Rejected approvals remain in history** — terminal states are never deleted or overwritten;
   `ApprovalDecision` rows are append-only and a case is never re-opened (`decision_id` unique).
5. **Approval cannot be performed twice** — `ApprovalLifecycle` rejects an action once the case has left
   the phase that action belongs to, backed by the DB's `UNIQUE (approval_case_id, role)` constraint as
   the real guarantee under concurrency (see next point).
6. **Concurrent approval attempts must be handled safely** — two racing calls for the *same* role
   (e.g. two "checker-decision" requests) both pass the in-memory status check, both try to insert an
   `ApprovalDecision` with the same `(caseId, role)`; the unique constraint lets exactly one commit and
   the loser's `DataIntegrityViolationException` is translated to `ConcurrentApprovalConflictException`.
   `ApprovalCase.version` (`@Version`) additionally protects the `status` update itself from a
   lost-update race. This is the same "DB constraint is the real lock, in-memory check is the fast
   path" strategy `UnderwritingService.startAttempt` already uses for its one-active-attempt rule.
7. **State transitions must be transactional** — `ApprovalService` methods are `@Transactional`; the
   decision-row insert and the case-status update commit or roll back together.

## Endpoints

```
POST /api/authority-matrix                          create an entry
GET  /api/authority-matrix                           list entries (matchOrder asc)
GET  /api/authority-matrix/{id}                       get one
PUT  /api/authority-matrix/{id}                       update one
DELETE /api/authority-matrix/{id}                     delete one

POST /api/credit-decisions/{decisionId}/approval-case  open a case (resolves required level)
GET  /api/credit-decisions/{decisionId}/approval-case  fetch the case for a decision

GET  /api/approval-cases/{caseId}                      case detail + its decisions
POST /api/approval-cases/{caseId}/maker-decision       record the maker's recommendation
POST /api/approval-cases/{caseId}/checker-decision     record the checker's final call
```

## Migrations

```
027-create-authority-matrix-entry.sql
028-seed-default-authority-matrix.sql   (A/B -> AUTO under 500k, else CREDIT_OFFICER under 2M, else catch-all SENIOR_CREDIT_MANAGER)
029-create-approval-case.sql
030-create-approval-decision.sql
```

## Tests

- `AuthorityMatrixResolverTest` — matching precedence, unbounded bounds, no-match, catch-all.
- `ApprovalLifecycleTest` — every legal/illegal transition pair.
- `AuthorityMatrixAdminIntegrationTest` — CRUD.
- `ApprovalWorkflowIntegrationTest` — full flow through the real DB: open -> maker -> checker -> APPROVED;
  self-approval rejected; insufficient checker authority rejected; double maker/checker action rejected;
  `ApprovalNotRequiredException` for a DECLINE / AUTO-level decision.

## Out of scope

Real user/authentication resolution of `actorLevel` (Week 10). Branch-based criteria (no branch entity
exists anywhere in this codebase; adding one purely for the matrix would be a speculative abstraction).
