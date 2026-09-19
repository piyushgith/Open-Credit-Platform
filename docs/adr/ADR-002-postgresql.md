# ADR-002: PostgreSQL + Liquibase

**Status:** Accepted (Week 1, holds through Week 12)

## Context

A credit platform's correctness rests as much on its schema as its Java: uniqueness (one selected
offer per application, one active underwriting attempt per case), partial indexes, optimistic
locking, and foreign-key integrity are all invariants that must hold even when application code has
a bug or two requests race. `my_docs/plan.md` originally named Flyway as the migration tool; the
actual project uses Liquibase — this ADR records what was actually built, not the original plan.

## Decision

PostgreSQL as the only datastore. Schema managed by `spring-boot-starter-liquibase`, with plain SQL
changesets (not Liquibase's XML/YAML DSL) under
`src/main/resources/db/changelog/changes/NNN-description.sql`, each registered as an `include` in
`db.changelog-master.yaml`. `spring.jpa.hibernate.ddl-auto=validate` — Hibernate never generates or
alters schema; it only verifies the entity mappings match what Liquibase already created, so a
mapping drift fails loudly at boot instead of silently at runtime.

Every migration is additive-only once shipped: fixing a mistake means a new numbered changeset, never
editing one already merged (see `040-add-missing-fk-indexes.sql` for an example — a Week 12 fix
added as a new changeset, not a rewrite of `019-create-score.sql`).

## Consequences

**Positive:** invariants like "at most one selected offer" or "at most one active underwriting
attempt" hold even under a concurrent-request race that Java-level checks alone can't prevent (see
[transaction-boundaries.md](../architecture/transaction-boundaries.md)). Plain SQL changesets are
directly readable/reviewable without learning Liquibase's own DSL.

**Negative:** no Testcontainers — every `@SpringBootTest` needs a real, reachable local (or CI
service-container) PostgreSQL instance; see
`my_plan/week12-production-hardening-plan.md`'s "Context" section for the concrete CI implication
this has.
