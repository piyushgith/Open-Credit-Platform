# ADR-006: Maker-Checker

**Status:** Accepted (Week 8, identity source changed in Week 10)

## Context

A `CreditDecision` above a configurable risk/amount threshold needs a second, independent human
sign-off before it's actionable — one person recommending, a different, sufficiently senior person
confirming. The two failure modes to prevent: the same person acting as both maker and checker
(self-approval), and a checker without enough authority for the case's resolved risk level.

## Decision

`AuthorityMatrixEntry` rows (configurable by `productType`/`riskGrade`/amount range) resolve a
`CreditDecision` to a `RequiredLevel` (`AUTO` / `CREDIT_OFFICER` / `SENIOR_CREDIT_MANAGER`); `AUTO`
means no case is opened at all. `ApprovalCase` tracks `PENDING_MAKER -> PENDING_CHECKER ->
APPROVED/REJECTED`, with each action recorded as an append-only `ApprovalDecision` row.

Self-approval and authority checks (`ApprovalService.recordDecision`) compare the *checker's*
identity/level against the *maker's* recorded identity and the case's required level. Until Week
10, that identity was asserted by the client in the request body — `ApprovalActionRequest`'s own
Javadoc flagged this as a known gap ("no user/security directory exists yet"). Week 10 closed it:
identity and level now come from the authenticated `AppUser` (via JWT), not the request body — see
[ADR-005](ADR-005-audit-strategy.md)'s sibling security work in
`my_plan/week10-audit-security-observability-plan.md`.

Concurrency: the Java-level `ApprovalLifecycle.assertCanAct` check is the fast path; the real guard
is `UNIQUE (approval_case_id, role)` at the database level, so two racing requests for the same role
can only ever have one winner (see
[transaction-boundaries.md](../architecture/transaction-boundaries.md)).

## Consequences

**Positive:** self-approval and insufficient-authority are structurally impossible to bypass via the
API, not just discouraged by client-side UX. A maker cannot fabricate a checker identity or level.

**Negative:** `AppRole` (`MAKER`/`CHECKER`/`ADMIN`) is a simplification of `my_docs/plan.md`'s
four named roles (Maker/Checker/Credit Officer/Admin) — "Credit Officer" is modeled as an
`ApprovalLevel` a `CHECKER` holds, not a fourth top-level role, to avoid representing the same
seniority concept twice. See `security/model/AppRole`'s Javadoc for the reasoning.
