# ADR-001: Modular Monolith

**Status:** Accepted (Week 1, holds through Week 12)

## Context

The platform needed strong transactional consistency across a loan's lifecycle (application ->
underwriting -> offer -> sanction -> disbursement) and across the independent financial-analysis ->
scoring -> decisioning -> maker-checker track. At the project's actual scale (a portfolio system,
not a bank-in-production), there is no throughput or team-boundary pressure that a distributed
architecture would solve.

## Decision

One Spring Boot application, one PostgreSQL database, domain-oriented Java packages
(`customer`, `loan`, `financial`, `scoring`, `decision`, `authority`, `offer`, `disbursement`,
`security`, `audit`, ...). Cross-module interaction is a direct method/repository call within one
JVM and one database transaction — never a network call, never an event bus (see
[ADR-008](ADR-008-event-driven-evolution.md) for why that's a deliberate deferral, not an
oversight).

## Consequences

**Positive:** a maker-checker approval, a disbursement tranche, and an audit row all commit or roll
back atomically with the business mutation they describe, with zero extra machinery. Deployment is
one artifact. Local development needs one database, not a cluster of fakes/stubs.

**Negative:** the whole application scales as one unit — a spike in read traffic on `financial`
analysis can't be scaled independently of `loan` write traffic. There is no compile-time boundary
preventing one module from reaching into another's repository (only convention — see
[domain-boundaries.md](../architecture/domain-boundaries.md)).

**Revisit when:** a specific module has a measured, independent scaling or team-ownership need that
a shared deployment can no longer satisfy — not preemptively. See "When Microservices Would Make
Sense" in the root [README.md](../../README.md).
