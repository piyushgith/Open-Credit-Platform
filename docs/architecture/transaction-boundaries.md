# Transaction Boundaries

## The pattern

Every `@Service` in this codebase is `@Transactional` at the class level (default propagation
`REQUIRED`), with read-only query methods separately annotated `@Transactional(readOnly = true)`.
Controllers carry no transaction annotations — they're thin, one call into a service per endpoint.

When service A calls service B within the same request (e.g.
`LoanApplicationService.sanction()` calling `OfferService.sanctionSelectedOffer()`), B's method
joins A's existing transaction rather than opening a new one. This means a domain object's
persisted-state mutation and its `AuditService.recordEvent`/`recordDataChange` call always commit
or roll back together — an audit write failure fails the business action with it, rather than
silently losing the trail. See [ADR-005](../adr/ADR-005-audit-strategy.md).

## Where the "real" concurrency guard lives

The Java-level checks in each service (`existsBy...` pre-checks, in-memory lifecycle validators
like `ApprovalLifecycle.assertCanAct`) are consistently documented in this codebase as the *fast
path* — the actual safety net under concurrent requests is a database constraint, with the
resulting `DataIntegrityViolationException`/`ObjectOptimisticLockingFailureException` translated to
a domain exception:

| Concurrency-sensitive operation | Fast-path check (Java) | Real guard (database) | Exception on conflict |
|---|---|---|---|
| Selecting an offer | `existsByApplicationIdAndStatus` | partial unique index on `offer(application_id) WHERE status='SELECTED'` | `OfferAlreadySelectedException` |
| Maker/checker acting on a case | `ApprovalLifecycle.assertCanAct` | `UNIQUE (approval_case_id, role)` | `ConcurrentApprovalConflictException` |
| Recording a disbursement tranche | request-reference uniqueness isn't pre-checked | `UNIQUE (disbursement_id, request_reference)` | `DuplicateDisbursementRequestException` |
| Updating `Disbursement.disbursedTotal` | read-then-add in Java | `@Version` optimistic lock | `ConcurrentDisbursementConflictException` |
| Opening a second approval case for one decision | `existsByDecisionId` | `UNIQUE (decision_id)` on `approval_case` | `DuplicateApprovalCaseException` |

Why this matters: two racing HTTP requests both pass the Java-level `existsBy...` check (it's a
read, not a lock) before either has written anything. The database constraint is what actually
decides which one wins; the Java check only avoids doing unnecessary work in the common
(non-racing) case.

## What is *not* wrapped in an explicit transaction

Simple `JpaRepository` read methods called directly from a `@RestController` (e.g.
`AuditController`'s `GET` endpoints) rely on Spring Data's own per-repository-method transaction —
there's no service layer or explicit `@Transactional` needed for a single read.
