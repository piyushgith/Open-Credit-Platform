# Week 10 — Audit, Security & Observability

## Context

`ApprovalActionRequest` (`authority/dto/ApprovalActionRequest.java`) already says the quiet part out
loud: its Javadoc reads *"`actorLevel` is asserted by the caller rather than looked up, since no
user/security directory exists yet (Week 10)."* That is the literal seam this week closes. A repo-wide
search confirms it's not just that one class — there is no `Authentication`/`Principal` usage anywhere,
no `performedBy`/`actorId` concept on any entity, no Spring Security dependency, no actuator dependency,
and no request/correlation id anywhere in `pom.xml` or `src/main`. Every maker/checker call today trusts
`actorUsername`/`actorLevel` fields supplied in the JSON body (`ApprovalCaseController` ->
`ApprovalService.recordDecision`).

There is also no audit trail at all — no table, no service. Every status transition since Week 2 already
happens at one well-defined call site owned by exactly one service (Week 9's plan calls this out
explicitly for `ApplicationStatus`), so audit does not need a generic mechanism bolted on top; it needs
to be called from those existing call sites.

Equally telling: `spring-modulith-starter-core`/`-runtime` are already dependencies, but a repo-wide
search for `ApplicationEventPublisher` / `@ApplicationModuleListener` finds zero hits. Nothing in this
codebase uses events — every cross-concern (Week 9's `OfferService` calling into `LoanApplicationService`,
Week 8's `ApprovalService` walking `Score -> FinancialAnalysisRun -> FinancialStatement ->
LoanApplication`) is a direct, explicit method call. Week 10 keeps that idiom: audit is an explicit
`AuditService` call made by the owning service at the same line it already persists its state change —
not a listener, not AOP, not a generic entity-diffing engine. That also means the audit row and the
business mutation share the same `@Transactional` boundary: if the audit write fails, the business action
rolls back with it. For a system whose non-negotiable principle #20 is "all important changes require
auditability," failing closed is the correct default, not an edge case to special-case away.

## Business audit — `audit_business_event`

One row per named event, mapped to the exact existing call site that already owns that transition:

| Event | Emitted by | Method |
|---|---|---|
| `APPLICATION_SUBMITTED` | `LoanApplicationService` | `submit` |
| `UNDERWRITING_STARTED` | `UnderwritingService` | `startAttempt` |
| `UNDERWRITING_COMPLETED` | `UnderwritingService` | `completeAttempt` |
| `DECISION_CREATED` | `DecisionService` | `decide` |
| `APPROVAL_DECISION_RECORDED` | `ApprovalService` | `recordDecision` (maker and checker both) |
| `OFFER_CREATED` | `OfferService` | `createOffer` |
| `OFFER_ACCEPTED` | `OfferService` | `selectOffer` |
| `SANCTION_APPROVED` | `OfferService` | `sanctionSelectedOffer` |
| `DISBURSEMENT_CREATED` | `DisbursementService` | `recordTranche` |

`APPLICATION_SUBMITTED` and `APPROVAL_DECISION_RECORDED` aren't named in `my_docs/plan.md`'s Week 10
list, but they're the same kind of event (a status/decision transition already owned by one service) and
maker-checker is exactly the case auditability matters most for, so they're added rather than skipped for
literal compliance with the list.

Columns: `id`, `event_type`, `entity_type`, `entity_id`, `actor_username`, `occurred_at`, `request_id`,
`correlation_id`, `details` (short free-text, same precedent as `RuleResult.description` — a plain
`VARCHAR`, not `jsonb`; nothing else in the schema uses `jsonb` and one wouldn't be justified for a single
human-readable line).

## Data-change audit — `audit_data_change`

**Scoping decision, stated up front:** this is not a blanket field-diff engine over all ~30 entities.
Building one (reflection over every `@Entity`, or pulling in Hibernate Envers) would be exactly the kind
of "generic framework-building" principle #8 rules out, for a benefit most of those fields don't need —
principle #20 says *important* changes require auditability, not *all* changes. Scope is the same status
columns the business-audit table above already watches: `LoanApplication.status`,
`UnderwritingAttempt.status`, `ApprovalCase.status`, `Offer.status`, `Disbursement.status` (plus
`disbursedTotal`, since that running total is the one the Week 9 plan calls out as the concurrency-guarded
invariant). A customer's phone number changing isn't tracked; the value that gates money moving is.

Columns: `id`, `entity_type`, `entity_id`, `field`, `old_value`, `new_value`, `changed_by`, `changed_at`,
`request_id`, `correlation_id` — `old_value`/`new_value` as text, since the fields in scope are all
enum-or-numeric and stringify losslessly.

"Audit records must not be casually editable/deletable through normal APIs" is satisfied by construction:
`AuditController` (below) only exposes `@GetMapping`s; no repository update/delete method is ever called
outside `AuditService`'s own insert paths. No DB-grant-level lockdown is added this week — call that out
as a deliberate simplification, not an oversight.

## Security

**Problem being solved:** there is no user directory, so the two moving parts are (a) invent a minimal one
and (b) stop trusting client-supplied identity on the approval endpoints specifically, since that's the
one place identity spoofing has a real financial consequence (`SelfApprovalException`,
`InsufficientApprovalAuthorityException` in `ApprovalService` today only work if the username/level in the
request body are honest).

**New `security` module**, same package layout as `authority`/`offer` (`model/`, `repository/`,
`support/`, `config/`, controller at module root):

- `AppUser` — `username`, `passwordHash` (BCrypt), `role` (`AppRole`: `MAKER`, `CHECKER`,
  `CREDIT_OFFICER`, `ADMIN` — the four roles `my_docs/plan.md` names), `approvalLevel`
  (`authority.model.ApprovalLevel`, nullable — only meaningful for a user allowed to act as checker),
  `active`.
- `JwtTokenService` — issues/parses a self-signed HS256 JWT (via `io.jsonwebtoken:jjwt-api/-impl/-jackson`
  — a small codec, not a framework; there is no external IdP to federate against, so a self-issued token
  is the simplest thing that demonstrates the concept). Secret/expiry come from `application.properties`
  via the same `${ENV_VAR:default}` pattern already used for `DB_URL`/`DB_USER`/`DB_PASSWORD`.
- `JwtAuthenticationFilter` (`OncePerRequestFilter`) — reads `Authorization: Bearer <token>`, populates
  `SecurityContext`.
- `SecurityConfig` — `spring-boot-starter-security` filter chain; `/api/auth/login`, `/swagger-ui/**`,
  `/v3/api-docs/**`, `/actuator/health` permitted, everything else requires authentication;
  `@EnableMethodSecurity` for `@PreAuthorize` on the sensitive endpoints below.
- `AuthController` — `POST /api/auth/login` (username/password -> JWT). No self-registration; users come
  from a demo seed migration (below), clearly labeled as demo-only credentials, matching principle #29
  ("verification > assumption") — nobody should mistake seeded logins for a real onboarding flow.

**What changes in `authority`:** `ApprovalActionRequest` drops `actorUsername`/`actorLevel` entirely —
`ApprovalService.recordDecision` reads the actor from `SecurityContextHolder` (`Authentication.getName()`)
and the checker's `approvalLevel` from the resolved `AppUser`, replacing the two lines in
`recordDecision` that currently read `request.getActorUsername()`/`request.getActorLevel()`. The
`SelfApprovalException`/`InsufficientApprovalAuthorityException` checks are unchanged in logic — only
where the two values come from changes, from "whatever the client claims" to "whoever the JWT says, at
whatever level their `AppUser` row has." `POST /api/approval-cases/{caseId}/maker-decision` requires
`ROLE_MAKER` or `ROLE_ADMIN`; `/checker-decision` requires `ROLE_CREDIT_OFFICER` or `ROLE_ADMIN`.

**What else gets `@PreAuthorize("hasRole('ADMIN')")`:** the three admin-config controllers —
`AuthorityMatrixAdminController`, `CreditPolicyAdminController`, `ScorecardAdminController` — since they
edit the policy/scorecard/matrix rows every downstream decision depends on. Everything else (customer,
loan, document, kyc, financial, scoring, offer, disbursement endpoints) requires only a valid JWT, no
specific role — narrower role-gating for those is explicitly deferred rather than inventing a full
permission matrix nobody asked for yet.

## Correlation IDs & structured logging

A `RequestContextFilter` (`OncePerRequestFilter`, registered ahead of the JWT filter) reads or generates
`X-Request-Id` per request and `X-Correlation-Id` (defaults to the request id when the client doesn't
supply one — e.g. a multi-step demo script that wants to tie several calls together), puts both into MDC
for logging and into a request-scoped holder `AuditService` reads from. Logback pattern in
`application.properties` is extended to include `%X{requestId}`/`%X{correlationId}`. No log
aggregation/tracing stack — that's explicitly Phase 2 (`my_docs/plan.md` §23: OpenTelemetry, Prometheus,
Grafana).

**Do not log:** the JWT itself, the raw password on login, or full request bodies on the existing
`GlobalExceptionHandler.handleUnexpected` catch-all (it currently logs the URI + exception only, which is
already safe — nothing to change there).

## Actuator

`spring-boot-starter-actuator`; expose `/actuator/health` and `/actuator/info` only
(`management.endpoints.web.exposure.include=health,info`), both permitted without auth. No other endpoint
exposed this week (`/actuator/env`, `/actuator/beans`, etc. would leak `DB_URL`/JWT secret material).

## Endpoints

```
POST /api/auth/login                          username/password -> { token, expiresAt }
GET  /api/audit/business-events?entityType=&entityId=
GET  /api/audit/data-changes?entityType=&entityId=
GET  /actuator/health
GET  /actuator/info
```

Plus the `@PreAuthorize` additions on the existing approval and admin-config endpoints described above.

## Migrations

```
035-create-app-user.sql
036-seed-demo-users.sql            (admin / officer / manager / maker demo accounts — comment-flagged as demo-only)
037-create-audit-business-event.sql
038-create-audit-data-change.sql
```

## Tests

- `JwtTokenServiceTest` — issue/parse round-trip, expiry, tampered signature rejected.
- Security integration test — unauthenticated request to a protected endpoint -> 401; authenticated but
  wrong role -> 403; correct role -> 200. Cover `maker-decision`/`checker-decision` and one admin-config
  endpoint.
- `AuditServiceTest` — business event and data-change rows carry the correct actor/request id/correlation
  id sourced from the request context.
- `RequestContextFilterTest` — supplied `X-Correlation-Id` is propagated; absent one is defaulted from the
  generated request id.
- Update `ApprovalWorkflowIntegrationTest` (existing) — requests no longer carry
  `actorUsername`/`actorLevel`; tests authenticate as seeded `AppUser`s instead. This is the one existing
  test file this week is guaranteed to touch.

## Out of scope

Full entity/field audit coverage beyond the five status-bearing fields named above (documented as a
deliberate scoping decision, not a gap). User self-registration, password reset, or any external
IdP/OAuth2/SSO integration. Log aggregation, tracing, metrics dashboards (Phase 2 per `my_docs/plan.md`
§23). Rate limiting or brute-force login protection. DB-grant-level (as opposed to API-level) lockdown of
the audit tables.
