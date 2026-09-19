# Open Credit Platform

An open-source credit decisioning platform: loan origination, underwriting, financial statement
analysis, deterministic credit scoring and decisioning, maker-checker approval, offer/sanction,
disbursement, and audit. Built with Java 25, Spring Boot 4, and PostgreSQL as a modular monolith.

The goal is not the largest possible application — it's demonstrating the ability to design and
build a complex financial system while keeping the architecture simple and explainable. See
[`my_docs/plan.md`](my_docs/plan.md) for the full 12-week build plan this project followed, and
[`my_plan/`](my_plan/) for the per-week implementation plans (each grounded in the actual code at
the time it was written, not just the aspirational plan).

## Problem

A credit decisioning system has to get several things right simultaneously: correct financial
arithmetic, deterministic and explainable decisions, controlled dual sign-off on risk, and a
complete, tamper-evident record of what happened and why — all while staying maintainable by a
small team, not requiring a distributed-systems investment from day one.

## Goals

- Demonstrate deterministic, auditable financial and credit decisioning.
- Demonstrate a maker-checker approval workflow that's structurally (not just conventionally) safe
  against self-approval and insufficient authority.
- Demonstrate that a modular monolith can carry real transactional and domain complexity without
  premature distributed-systems machinery.
- Keep AI (once built — see [Roadmap](#roadmap)) strictly advisory, never decision-making.

## Non-Goals

Not a production bank core system. Not optimized for maximum feature count. No mobile app, no
custom rules DSL, no Kafka/Redis/microservices without a concrete, demonstrated need — see
[docs/adr/ADR-008-event-driven-evolution.md](docs/adr/ADR-008-event-driven-evolution.md) for what
would actually justify introducing those.

## Architecture

One Spring Boot application, one PostgreSQL database, domain-oriented packages. There are two
routes to a credit decision, both ending in the same `LoanApplication` lifecycle and both able to
reach `offer -> sanction -> disbursement` — full explanation, module boundaries, and why, in
[docs/architecture/overview.md](docs/architecture/overview.md).

```
Track A (simple):  Loan Application -> FOIR check (submit()) -> Offer -> Sanction -> Disbursement
Track B (full):    Loan Application -> Financial Analysis -> Scoring -> Credit Decision
                        -> Maker-Checker Approval -> Offer -> Sanction -> Disbursement
```

## Domain Model

See [docs/architecture/domain-boundaries.md](docs/architecture/domain-boundaries.md) for every
module's aggregate root and the database invariant that protects it.

## Loan Lifecycle

[docs/domain/loan-lifecycle.md](docs/domain/loan-lifecycle.md) — states, transitions, endpoints.

## Financial Analysis

[docs/domain/financial-analysis.md](docs/domain/financial-analysis.md) — the raw-facts -> derived-
facts -> ratios -> risk-indicators pipeline, and why each stage is always recomputed rather than
trusted from input. Design rationale: [ADR-003](docs/adr/ADR-003-financial-calculation-engine.md).

## Credit Scoring

[docs/domain/credit-scoring.md](docs/domain/credit-scoring.md) — configurable scorecards, factor
resolution, missing-value handling.

## Decision Engine

[docs/domain/decisioning.md](docs/domain/decisioning.md) — deterministic rule evaluation
(`HARD`/`SOFT` severities, hard-fail-forces-decline logic), full decision explainability via
`passedRules`/`failedRules`. Design rationale: [ADR-004](docs/adr/ADR-004-credit-decision-engine.md).

## Authority Matrix

Configurable `(productType, riskGrade, amount range) -> required approval level` resolution,
documented alongside maker-checker in [docs/domain/decisioning.md](docs/domain/decisioning.md) and
[ADR-006](docs/adr/ADR-006-maker-checker.md).

## Audit

Every important state transition is recorded explicitly by the service that owns it — business
events and field-level data changes, both tagged with actor, request id, and correlation id.
Design rationale and scoping decisions: [ADR-005](docs/adr/ADR-005-audit-strategy.md).

## AI Assistant

**Not yet implemented.** Designed (not built) in
[my_plan/week11-ai-credit-assistant-plan.md](my_plan/week11-ai-credit-assistant-plan.md) and
[ADR-007](docs/adr/ADR-007-ai-assistant.md): a boundary where AI reads only already-serialized
response DTOs from the decision/scoring/financial modules — zero repository access — so it
structurally cannot write into a credit decision.

## Security

Stateless, self-issued JWT (no external IdP — this is a portfolio project, not integrated with a
real identity provider). Every endpoint requires authentication except login, Swagger, and
`/actuator/health`/`/info`; maker/checker actions and admin-config endpoints additionally require a
specific role. Demo logins are seeded, not self-registered — see
[`036-seed-demo-users.sql`](Open-Credit-Platform/src/main/resources/db/changelog/changes/036-seed-demo-users.sql).
Design rationale: [my_plan/week10-audit-security-observability-plan.md](my_plan/week10-audit-security-observability-plan.md).

## Database Design

PostgreSQL, schema-managed by Liquibase (plain SQL changesets, never edited once shipped — see
[ADR-002](docs/adr/ADR-002-postgresql.md)). `spring.jpa.hibernate.ddl-auto=validate`: Hibernate
never generates schema, only verifies its mappings match what Liquibase already created.

## Transaction Strategy

Every service is `@Transactional`; a cross-module call joins the caller's existing transaction
rather than opening a new one, so a business mutation and its audit row commit or roll back
together. Full explanation: [docs/architecture/transaction-boundaries.md](docs/architecture/transaction-boundaries.md).

## Concurrency

The in-memory checks in each service (`existsBy...`, lifecycle validators) are the fast path; the
real guard against a race between two concurrent requests is always a database constraint —
partial unique indexes, composite unique constraints, or `@Version` optimistic locking, each
translated to a specific domain exception. Concrete table of every concurrency-sensitive operation
in the codebase: [docs/architecture/transaction-boundaries.md](docs/architecture/transaction-boundaries.md).

## Testing

JUnit 5 + AssertJ + Mockito for unit tests; `@SpringBootTest` + MockMvc integration tests against a
**real local PostgreSQL** (no Testcontainers — see [ADR-002](docs/adr/ADR-002-postgresql.md) and
the note below). Run `./mvnw test` from `Open-Credit-Platform/` with Postgres reachable at
`DB_URL`/`DB_USER`/`DB_PASSWORD` (defaults match `docker compose up postgres`).

## Running Locally

```bash
cd Open-Credit-Platform
docker compose up postgres -d      # or point DB_URL/DB_USER/DB_PASSWORD at your own instance
./mvnw spring-boot:run
```

Or run everything (app + Postgres) in containers: `docker compose up --build` from the same
directory. API docs at `http://localhost:8080/swagger-ui.html`.

## API Examples

Full REST surface: [docs/api/api-overview.md](docs/api/api-overview.md). A complete, runnable,
end-to-end walkthrough (both tracks, plus the audit trail) is in
[`scripts/development/demo.sh`](scripts/development/demo.sh) — requires `curl`/`jq` and a running
instance:

```bash
./scripts/development/demo.sh
```

## Architecture Decisions

Every ADR: [`docs/adr/`](docs/adr/) — modular monolith, PostgreSQL/Liquibase, the financial
calculation engine, the credit decision engine, the audit strategy, maker-checker, the (not yet
built) AI boundary, and the event-driven evolution path deliberately not taken yet.

Diagrams (system context, module boundaries, loan lifecycle, financial analysis, credit decision
flow, audit flow): [`docs/diagrams/`](docs/diagrams/), as Mermaid rather than static images —
GitHub renders them directly.

## Future Evolution

See [ADR-008](docs/adr/ADR-008-event-driven-evolution.md). Short version: `spring-modulith` is
already a dependency but unused for events by design — direct calls are sufficient at this
project's actual scale. The concrete next step, independent of any event/Kafka decision, is adding
`@ApplicationModule` boundary annotations and a `ModularityTests` verification test, which would
turn today's convention-only module boundaries into mechanically-enforced ones.

## Why Modular Monolith?

[ADR-001](docs/adr/ADR-001-modular-monolith.md). In short: this project needed strong transactional
consistency across a loan's lifecycle and across the independent scoring/decisioning/approval
track, and nothing about its actual scale creates pressure a shared deployment can't handle.

## When Microservices Would Make Sense

When a specific module has a *measured*, independent scaling or team-ownership need a shared
deployment genuinely can't satisfy — e.g. `financial` analysis under sustained heavy batch load
while `loan` needs to stay fast and interactive. Not before that need is real and measured. See
[ADR-001](docs/adr/ADR-001-modular-monolith.md)'s "Revisit when" section.

## Scaling Strategy

At small scale (thousands of applications/year), today's architecture is sufficient as-is. At
larger scale, the first moves would be: read replicas for the analytical/reporting queries (audit,
scoring history), then `@ApplicationModule`-verified boundaries (see
[ADR-008](docs/adr/ADR-008-event-driven-evolution.md)) as groundwork for extracting the first
module whose scaling need is actually measured and independent — most plausibly `financial`
analysis, which is read-heavy and already architecturally separate from the write-heavy loan
lifecycle.

## Limitations

- No AI assistant yet (Week 11 — designed, not built).
- Track A (the simple FOIR-based lifecycle) and Track B (financial analysis / scoring / decisioning
  / maker-checker) each independently drive `LoanApplication` to `OFFERED`/`DECLINED`, but the two
  don't compose on a single application — running both on the same application just means whichever
  one gets there first wins, and the other becomes a no-op. See
  [docs/architecture/overview.md](docs/architecture/overview.md).
- No Testcontainers — tests need a real local/CI Postgres.
- Module boundaries are convention-enforced, not `spring-modulith`-verified yet.
- No self-registration for user accounts; login is a fixed demo-seeded list.

## Roadmap

- **Week 11 (not started):** AI Credit Assistant — decision explanation, suggested questions,
  credit-memo drafting, all reading only already-serialized response DTOs. Plan:
  [my_plan/week11-ai-credit-assistant-plan.md](my_plan/week11-ai-credit-assistant-plan.md).
- `@ApplicationModule` boundary verification (see [ADR-008](docs/adr/ADR-008-event-driven-evolution.md)).
- Testcontainers, to decouple the test suite from a locally-running Postgres.
- A real decision on whether an application should ever be allowed through *both* Track A and
  Track B, and if so, which one's outcome should win — today it's simply "whichever reaches a
  terminal `ApplicationStatus` first."

## License

[MIT](LICENSE)
