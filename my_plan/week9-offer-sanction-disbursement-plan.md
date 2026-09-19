# Week 9 — Offer, Sanction & Disbursement

## Context

`ApplicationStatus` (`loan/model/ApplicationStatus.java`) has carried `OFFERED -> SANCTIONED -> DISBURSED`
since Week 2, and `LoanApplicationService` already has stub `sanction()`/`disburse()` methods that do a
bare status flip with no backing domain object — literally a seam left for this week. Weeks 6-8 (scoring,
credit policy/rules, authority matrix/maker-checker) are a deliberately independent analytical/approval
track that "never writes back to `LoanApplication`" (see `RequiredAuthority`'s and `ApprovalCase`'s
Javadoc). Week 9 does not try to merge that track in; it builds Offer/Sanction/Disbursement directly on
top of the `ApplicationStatus` lifecycle that has been there since Week 2, using the FOIR-based
underwriting decision (`LoanApplication.decision` / `decisionDetails`) as "successful underwriting."

Per `my_docs/plan.md` §5/§7, this is two modules, not one: `offer` (Offer, Sanction, `OfferStatus`) and
`disbursement` (Disbursement, DisbursementTranche, `DisbursementStatus`) — mirroring the `authority`
module's package layout (`model/`, `repository/`, `exception/`, `dto/`, controllers/services at the
module root).

## Business flow implemented

```
Underwriting APPROVED (application at OFFERED)
   -> create one or more Offers (amount <= approved amount, tenure within product bounds,
      rate fixed from the underwriting decision, EMI recomputed via the existing EmiCalculator)
   -> select exactly one Offer
   -> Sanction (snapshots the selected offer's terms; application -> SANCTIONED)
   -> one or more Disbursement tranches against the Sanction (application -> DISBURSED once the
      running total equals the sanctioned amount)
```

"Multiple offers" is resolved as customer choice over amount/tenure at the already-decided rate, not
re-pricing — re-pricing is a scoring/policy concern already covered (independently) by Weeks 6-8.

## Domain model

**`Offer`** (`offer` module) — one row per generated offer; `applicationId` (not unique — many offers
per application), `offerAmount`, `interestRate`, `tenureMonths`, `monthlyEmi`, `status` (`OfferStatus`:
`ACTIVE`, `SELECTED`), `createdAt`, `@Version`. A partial unique index
(`WHERE status = 'SELECTED'`) enforces at most one selected offer per application at the database level;
`OfferService` also pre-checks with `existsBy...` the same way `ApprovalService.openCase` pre-checks
`existsByDecisionId` before relying on the DB constraint as the concurrency backstop.

**`Sanction`** (`offer` module) — one per application (`application_id UNIQUE`, like `UnderwritingCase`),
snapshotting `offerId`, `sanctionedAmount`, `interestRate`, `tenureMonths`, `monthlyEmi` at sanction time.
No status enum — existence is the state (matches `my_docs/plan.md`'s domain model, which lists no
`SanctionStatus`).

**`Disbursement`** (`disbursement` module) — one per `Sanction` (`sanction_id UNIQUE`, lazily created on
the first tranche exactly like `UnderwritingService.startAttempt` lazily creates the `UnderwritingCase`);
`disbursedTotal` (running total), `status` (`DisbursementStatus`: `IN_PROGRESS`, `COMPLETED`), `@Version`
for optimistic locking on the running total (mirrors `ApprovalCase.version`).

**`DisbursementTranche`** — one per disbursement request; `trancheNumber` (sequential), `amount`,
`requestReference` (client-supplied idempotency key), `createdAt`. `UNIQUE (disbursement_id,
request_reference)` is the real guard against duplicate requests, same "DB constraint is the lock"
pattern as `approval_decision`'s `UNIQUE (approval_case_id, role)`.

## Business rules -> mechanism

1. **Offer can only be created from successful underwriting** — `application.status == OFFERED &&
   application.decision == APPROVED`, else `OfferNotEligibleException`.
2. **Only valid offers can be selected** — offer must belong to the application, be `ACTIVE`, and the
   application must still be `OFFERED`; no offer already `SELECTED` for the application (pre-check +
   partial unique index) -> `OfferAlreadySelectedException` on conflict.
3. **Sanction requires an eligible offer** — `LoanApplicationService.sanction` still runs
   `ApplicationLifecycle.assertTransition` first (preserves the existing
   `sanctionBeforeSubmitIsRejectedAsIllegalTransition` test), then delegates to
   `OfferService.sanctionSelectedOffer`, which requires a `SELECTED` offer (`NoOfferSelectedException`)
   and no pre-existing `Sanction` (`DuplicateSanctionException`, defensive).
4. **Disbursement requires sanctioned offer** — `DisbursementService.recordTranche` looks up the
   `Sanction` by `applicationId`; none found -> `SanctionNotFoundException`.
5. **Total disbursed amount cannot exceed sanctioned amount** — `disbursedTotal + amount <=
   sanctionedAmount`, else `DisbursementExceedsSanctionedAmountException`; the read-then-write on
   `Disbursement.disbursedTotal` is protected by `@Version` against a lost update under concurrent
   tranche requests (`ConcurrentDisbursementConflictException` on `ObjectOptimisticLockingFailureException`).
6. **Duplicate disbursement requests must be prevented** — `UNIQUE (disbursement_id, request_reference)`;
   a losing insert's `DataIntegrityViolationException` becomes `DuplicateDisbursementRequestException`.
7. **Each tranche must be independently traceable** — own id, sequential `trancheNumber`, amount,
   `requestReference`, `createdAt`.
8. **All monetary calculations use BigDecimal** — reuses the existing `EmiCalculator` unchanged.
9. **Important state transitions are transactional** — `OfferService`/`DisbursementService` methods are
   `@Transactional`, same as every other Week 6-8 service.

`LoanApplicationService` keeps sole ownership of `ApplicationStatus` mutation (the established pattern:
it never lets a collaborator write application status — see `runUnderwriting` delegating to
`UnderwritingService` for attempt bookkeeping only). `OfferService`/`DisbursementService` return the
domain result; `LoanApplicationService` decides and persists the status transition around it.

## Endpoints

```
POST /api/loans/{reference}/offers                    create an offer (body: tenureMonths, optional requestedAmount)
GET  /api/loans/{reference}/offers                     list offers for the application
POST /api/loans/{reference}/offers/{offerId}/select    select an offer

GET  /api/loans/{reference}/sanction                   read sanction detail

POST /api/loans/{reference}/sanction                   (existing path) sanction the selected offer
GET  /api/loans/{reference}/disbursements              read disbursement + tranche history
POST /api/loans/{reference}/disbursements               (replaces the old parameterless /disburse) record a tranche
```

The old `POST /api/loans/{reference}/disburse` (no body, stub) is removed; it never persisted a real
disbursement record, so there is no behavior to preserve. The integration test that exercised it is
updated to go through offer creation -> selection -> sanction -> tranche disbursement.

## Migrations

```
031-create-offer.sql              (+ partial unique index on application_id WHERE status = 'SELECTED')
032-create-sanction.sql
033-create-disbursement.sql
034-create-disbursement-tranche.sql
```

## Tests

- `OfferServiceTest`/pure validation paths where practical (amount/tenure bounds reuse
  `LoanProductConstraintViolationException`, same as `LoanApplicationService.validateAgainstProduct`).
- Extend `LoanApplicationIntegrationTest`'s golden path: submit -> approve -> create offer -> select ->
  sanction -> disburse in two tranches -> DISBURSED.
- New failure-path integration tests: offer creation before approval, selecting an already-selected
  offer, sanctioning with no offer selected, over-sanctioned-amount disbursement, duplicate
  `requestReference`.

## Out of scope

Re-pricing/renegotiating an offer's rate (that is Weeks 6-8's policy/scoring territory). Reversing or
cancelling a disbursed tranche. Wiring the Week 7/8 `CreditDecision`/`ApprovalCase` track into this
lifecycle — they remain independent, per their own documented design.
