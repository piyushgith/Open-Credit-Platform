# ADR-005: Audit Strategy

**Status:** Accepted (Week 10)

## Context

`my_docs/plan.md` non-negotiable principle #20: all important changes require auditability. By
Week 10, `spring-modulith-starter-core`/`-runtime` had been dependencies since early on but were
never used for events — a repo-wide search found zero `ApplicationEventPublisher`/
`@ApplicationModuleListener` usage. Every cross-module interaction in this codebase is already a
direct, explicit method call (see [domain-boundaries.md](../architecture/domain-boundaries.md)).

Two designs were considered: (a) a generic entity/field-diffing mechanism (reflection over every
`@Entity`, or Hibernate Envers) applied uniformly to all ~30 entities, or (b) explicit calls at the
specific points that already mutate state.

## Decision

**(b): explicit calls, no generic mechanism.** `AuditService.recordEvent`/`recordDataChange` are
called directly by the owning service at the exact line it already persists its status change —
e.g. `UnderwritingService.startAttempt`/`completeAttempt` emit `UNDERWRITING_STARTED`/
`_COMPLETED`; `OfferService.selectOffer` emits `OFFER_ACCEPTED`. This keeps the existing
no-events idiom rather than introducing a new one for audit alone.

Data-change audit is deliberately scoped to the fields that actually gate a business transition —
`LoanApplication.status`, `UnderwritingAttempt.status`, `ApprovalCase.status`, `Offer.status`,
`Disbursement.status`/`disbursedTotal` — not a blanket diff of every column on every entity. A
customer's phone number changing isn't tracked; the value that gates money moving is. This is a
scoping decision made explicitly, not a gap discovered later.

`AuditService` methods run inside the caller's own `@Transactional` method (default `REQUIRED`
propagation): the audit row and the business mutation commit or roll back together. An audit write
failure fails the business action with it — for a system whose principle is "must never miss an
audit trail," failing closed is correct, not an edge case to special-case away.

## Consequences

**Positive:** the audit trail's correctness depends on the same discipline already governing every
other state mutation in the codebase (one owning service, one point of truth) rather than on a
separately-trusted generic mechanism. No new dependency (Envers) or paradigm (events) introduced.

**Negative:** adding a new auditable field requires an explicit `recordDataChange` call at each
mutation site, rather than "just works" coverage from a generic listener. If a future reviewer
wants full entity/field coverage, that is a known, explicit scope decision to revisit — not a bug.
