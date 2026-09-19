# CLAUDE.md

Guidance for Claude Code (or any agent) working in this repository. The Maven project lives in
[`Open-Credit-Platform/`](Open-Credit-Platform/) — `cd` there before running `./mvnw`.

## What this is

An open-source credit decisioning platform (loan origination -> KYC -> underwriting -> financial
analysis -> credit scoring -> deterministic decisioning -> maker-checker approval -> offer/sanction
-> disbursement -> audit), built to demonstrate Senior Tech Lead / Solution Architect-level system
design, not to be the largest possible application. The full 12-week build plan and per-week
implementation plans are in [`my_docs/plan.md`](my_docs/plan.md) and
[`my_plan/`](my_plan/) — read the relevant week's plan before touching that area; it documents the
actual design decisions and their rationale, not just what to build.

## Stack facts (check before assuming otherwise)

- **Java 25, Spring Boot 4.1.1.** Some starter artifact names changed in Boot 4 —
  `spring-boot-starter-webmvc` and `spring-boot-starter-webclient`, not `-web`/`-webflux`.
- **Liquibase, not Flyway** — despite what `my_docs/plan.md` says. Changesets are plain SQL files
  under `src/main/resources/db/changelog/changes/`, numbered sequentially (currently up to `040`),
  each registered in `db.changelog-master.yaml`. Never edit a shipped changeset; add a new one.
- **Jackson 3** (`tools.jackson`, not `com.fasterxml.jackson`) is the app's own serialization
  engine — see `application.properties`. Third-party libraries (e.g. `jjwt-jackson`) still pull in
  classic `com.fasterxml.jackson` transitively for their own internal use; that's normal and not a
  conflict.
- **`spring-modulith-starter-core`/`-runtime` are dependencies but unused for events.** No
  `ApplicationEventPublisher`/`@ApplicationModuleListener` exists anywhere in this codebase.
  Cross-module reads go through direct repository calls (e.g. `DecisionService` reading `scoring`'s
  `Score` and `financial`'s `FinancialRatio` directly) and cross-module writes go through the owning
  module's service (e.g. `OfferService.sanctionSelectedOffer`, called by
  `LoanApplicationService.sanction`). Match this pattern — don't introduce eventing to solve a
  problem the existing direct-call pattern already solves.
- **No Testcontainers.** Every `@SpringBootTest` runs against a real, persistent local PostgreSQL
  via `DB_URL`/`DB_USER`/`DB_PASSWORD` (defaults: `jdbc:postgresql://localhost:5432/open_credit`,
  `postgres`/`password`). Have a local Postgres running (`docker compose up postgres` from
  `Open-Credit-Platform/`) before running tests.
- **Security is stateless JWT**, self-issued (`security` module) — no external IdP. Demo logins are
  seeded by `036-seed-demo-users.sql`; see that file's comment for the list. Business/data-change
  audit (`audit` module) is written by explicit `AuditService` calls at the exact line each owning
  service already persists its state change — not a listener, not AOP. See
  `my_plan/week10-audit-security-observability-plan.md` for the reasoning.

## Non-negotiable engineering principles

(Full list: `my_docs/plan.md` §3. The ones most often relevant when editing this codebase:)

1. Modular monolith. No microservices, no Kafka, no Redis unless a real requirement demonstrates one
   — none has yet.
2. No speculative abstractions, no unnecessary interfaces, no generic framework-building. Three
   similar lines beat a premature abstraction.
3. Keep controllers thin; business logic lives in the module's service.
4. Database constraints are part of business correctness, not a backstop — see how `Offer`'s
   partial unique index, `ApprovalDecision`'s composite unique constraint, and
   `Disbursement.version` are each the *real* concurrency guard, with the service-level check as
   only the fast path.
5. Every important financial calculation requires tests. Use `BigDecimal`, never `double`/`float`,
   for anything monetary.
6. Financial and credit decisions must be deterministic and explainable. AI (Week 11, not yet
   implemented) must never make or override a credit decision — see `my_docs/plan.md`'s AI section
   and `my_plan/week11-ai-credit-assistant-plan.md` for the boundary this implies.
7. All important state changes require auditability — see the `audit` module.
8. Accuracy over speed; verification over assumption. If a requirement is ambiguous, stop and ask
   rather than guess.

## Workflow

1. Inspect the relevant existing code and its module's own plan doc in `my_plan/` before writing
   anything — most modules document *why* they're shaped the way they are, and that context changes
   what "the smallest correct change" looks like.
2. State assumptions and a short plan before implementing when a requirement is ambiguous.
3. Implement the smallest correct change. Don't refactor unrelated code in the same pass.
4. Add or update tests, then actually run them (`./mvnw test` from `Open-Credit-Platform/`) — don't
   report a change complete without having run the affected tests against the local Postgres.
5. Never run `git commit` unless explicitly asked to.

## Running locally

```
cd Open-Credit-Platform
docker compose up postgres -d      # or point DB_URL/DB_USER/DB_PASSWORD at your own instance
./mvnw spring-boot:run
```

API docs: `http://localhost:8080/swagger-ui.html`. See [`README.md`](README.md) for a full walkthrough
and [`scripts/development/demo.sh`](scripts/development/demo.sh) for a scripted end-to-end demo.
