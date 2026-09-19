# Week 12 — Production Hardening + Portfolio

## Context

This week adds nothing new to the domain. Per `my_docs/plan.md` §8: "This week is NOT about adding
features. It is about proving the project works." Concretely, several Week 1 deliverables
(`my_docs/plan.md` §5/§8) were never actually created — the repo went straight into weekly feature builds
without them: there is no `docs/` folder, no ADRs, no `CLAUDE.md`, no `docker-compose.yml`, no CI
workflow, and `README.md` is still its original two-line stub. Week 12 is the first and only point where
backfilling those is honest — by now there's eleven weeks of real, implemented decisions to document
instead of eleven weeks of aspirational ones.

Two concrete, evidence-based findings from inspecting the current state (not hypothetical review targets):

1. **Missing FK indexes exist, but only in two specific places, not everywhere.** Every migration from
   Week 8 onward (`027` through `034`) diligently pairs each foreign key with its own `CREATE INDEX` —
   `idx_offer_application_id`, `idx_disbursement_tranche_disbursement_id`,
   `idx_approval_decision_case_id`, etc. But `019-create-score.sql` indexes `analysis_run_id` and not the
   equally-real FK `scorecard_id`, and `024-create-credit-decision.sql` indexes `score_id` and not
   `policy_id`. Both missing columns are the *second* column of a composite `UNIQUE` constraint
   (`uq_score_run_scorecard (analysis_run_id, scorecard_id)`, `uq_credit_decision_score_policy (score_id,
   policy_id)`) — Postgres's automatic unique index only serves lookups whose leading column matches, so
   "which scores used scorecard X" / "which decisions were made under policy Y" (real admin/reporting
   queries once `ScorecardAdminController`/`CreditPolicyAdminController` are in daily use) still do a full
   table scan. This is the targeted fix, not a vague "review indexes" task.
2. **No Testcontainers.** `LoanApplicationIntegrationTest` says so directly in a comment: *"a persistent
   local Postgres (no Testcontainers yet)"* — every `@SpringBootTest` in the 26 existing test files hits a
   real Postgres via the same `DB_URL`/`DB_USER`/`DB_PASSWORD` env vars `application.properties` reads.
   That means CI can't just `mvn test` in a vacuum; the workflow needs a Postgres service container wired
   to those same three env vars, or every integration test fails at startup.

## Tasks

### 1. Adversarial review, run for real

Execute `my_docs/plan.md` §9's "Full Adversarial Review" prompt against the whole repository (via the
`code-review` skill or an equivalent manual pass), producing the specified
`| Severity | Class/File | Line | Method | Problem | Why It Matters | Recommended Fix |` table and
nothing else on the first pass — no fixes yet. Then work findings one at a time through §10's Sequential
Review Prompt discipline: verify each finding is real before touching code, smallest safe fix, update
tests, report exactly what changed. Do not batch-fix.

### 2. Targeted database review

Fix the two missing indexes identified above (`score.scorecard_id`, `credit_decision.policy_id`) as new
migrations (additive `CREATE INDEX`, never edit a shipped changeset). Then run §12's Database Review
Prompt across all 38 migrations for the rest of its checklist (nullable columns that shouldn't be,
monetary types, optimistic-locking coverage) — expect fewer findings than a typical Week-12 review would,
precisely because the last five weeks' migrations already show this level of care; the useful output here
is confirming that, not assuming the schema is undisciplined.

### 3. Concurrency review

Run §13's Concurrency Review Prompt specifically against the three concurrency-sensitive mechanisms the
codebase already documents in its own comments: `Offer`'s partial unique index
(`uq_offer_selected_per_application`), `ApprovalDecision`'s `UNIQUE (approval_case_id, role)`, and
`Disbursement.disbursedTotal`'s `@Version` optimistic lock. The question isn't "do these exist" (they do)
but "are they actually exercised by a concurrent-request test," not just designed correctly on paper.

### 4. Financial calculation review

Run §11's Financial Calculation Review Prompt against the three files it actually applies to:
`financial/support/DerivedFactCalculator.java`, `RatioCalculator.java`, `RiskIndicatorEvaluator.java`.
Trace raw line items -> derived facts -> ratios -> risk indicators per the documented processing order
from the Week 4 plan.

### 5. `docs/`

Populate the structure `my_docs/plan.md` §5 specifies, written from what was actually built, not
aspirationally:

```
docs/architecture/overview.md            modular monolith, module boundaries, the actual package list
docs/architecture/domain-boundaries.md
docs/architecture/transaction-boundaries.md
docs/adr/ADR-001-modular-monolith.md
docs/adr/ADR-002-postgresql.md
docs/adr/ADR-003-financial-calculation-engine.md
docs/adr/ADR-004-credit-decision-engine.md
docs/adr/ADR-005-audit-strategy.md          (Week 10 — explicit-call audit, not event-based)
docs/adr/ADR-006-maker-checker.md
docs/adr/ADR-007-ai-assistant.md            (Week 11 — the DTO-only boundary)
docs/adr/ADR-008-event-driven-evolution.md  (why Modulith is a dependency but unused for events today)
docs/domain/{loan-lifecycle,underwriting,financial-analysis,credit-scoring,decisioning}.md
docs/api/api-overview.md
```

ADR-008 is worth being candid in: `spring-modulith-starter-core`/`-runtime` have been dependencies since
early on and are still unused for actual module verification (`ApplicationModules.of(...).verify()` has
never been run — no `ModularityTests`-style file exists). Adding `@ApplicationModule` annotations across
the ~15 packages and one verification test is a good, low-risk hardening task for this week: it's using a
dependency already paid for, and it would mechanically enforce the Week 11 boundary (e.g. that `decision`
never ends up depending on `ai`) instead of that boundary resting on "nobody wrote that import."

### 6. `CLAUDE.md`

Never created (Week 1 deliverable per §5). Write it now, summarizing §3's non-negotiable engineering
principles for future Claude Code sessions working in this repo.

### 7. README rewrite

Replace the current two-line stub with the structure in §15 — Problem, Goals, Architecture, Domain Model,
Loan Lifecycle, Financial Analysis, Credit Scoring, Decision Engine, Authority Matrix, Audit, AI
Assistant, Security, Database Design, Transaction Strategy, Concurrency, Testing, Running Locally, API
Examples, Architecture Decisions, Future Evolution, Why Modular Monolith?, When Microservices Would Make
Sense, Scaling Strategy, Limitations, Roadmap.

### 8. Demo dataset + scripted walkthrough

One executable path through §16's full demo scenario (SME customer -> application -> documents -> KYC ->
financial statements -> analysis -> ratios -> score -> credit rules -> REFER -> authority matrix ->
checker review -> APPROVE -> offer -> sanction -> disbursement -> audit history), as a
`scripts/development/demo.sh` driving the REST API end to end. Any seed data is Liquibase-context-gated
(or a separate script run manually) so it never applies to the default/test changelog run — it must not
alter what the 26 existing tests see.

### 9. `docker-compose.yml`

Postgres service + app service, wired through the *existing* `DB_URL`/`DB_USER`/`DB_PASSWORD` env vars —
no new Spring profile needed, since `application.properties` already externalizes all three via
`${ENV_VAR:default}`.

### 10. CI

`.github/workflows/ci.yml`: a Postgres service container (matching the env vars above), then
`./mvnw test`. This is not optional the way it might be in a project with Testcontainers — without a
Postgres service in the workflow, every integration test fails at `@SpringBootTest` startup, not just
skips.

### 11. Portfolio checklist cross-check

Final pass against §17's checklist once the above lands.

## Out of scope

Adding Testcontainers to replace the persistent-Postgres integration test setup (a real improvement, but a
test-infrastructure change, not a hardening/documentation task — flag it as a good post-Week-12 follow-up
rather than doing it silently as part of "CI setup"). Any Phase-2 items from §23 (Kafka, Redis,
OpenTelemetry, Prometheus, Grafana, cloud deployment, microservice extraction) — those are explicitly
"only after the core system is stable," and their absence is not a Week 12 finding.
