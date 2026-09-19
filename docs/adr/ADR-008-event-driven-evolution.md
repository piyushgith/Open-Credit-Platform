# ADR-008: Event-Driven Evolution Path

**Status:** Accepted as a deferred option — no event-driven code exists yet; this ADR records why,
and what would trigger building it.

## Context

`spring-modulith-starter-core`/`-runtime` have been project dependencies since early in the build,
yet a repo-wide search finds zero uses of `ApplicationEventPublisher` or
`@ApplicationModuleListener`. Every cross-module interaction — `DecisionService` reading `scoring`'s
`Score`, `LoanApplicationService` calling `OfferService.sanctionSelectedOffer` — is a direct,
synchronous, same-transaction call (see
[domain-boundaries.md](../architecture/domain-boundaries.md)). `my_docs/plan.md` §23 explicitly
lists Kafka, event-driven architecture, and microservice extraction as Phase 2, "only after the
core system is stable" — not a Week 1-12 deliverable.

## Decision

Keep direct calls for now. Don't introduce Spring Modulith events, Kafka, or any async boundary
speculatively. The dependency stays in `pom.xml` unused until a concrete requirement justifies it —
this is itself a demonstration of "no generic framework-building" and "no speculative
abstractions" (`my_docs/plan.md` principles #7/#8): having a capability available isn't a reason to
use it.

**What would trigger building it:** a module needing to scale or deploy independently of the rest
(e.g. `financial` analysis under heavy batch load while `loan` stays interactive), or a genuine
need for eventual consistency across a boundary that synchronous calls can no longer serve
correctly at the read/write volume involved. Neither exists today.

## A smaller, immediately useful step

Independent of Kafka/events: add `@ApplicationModule` annotations to each package and a
`ModularityTests` class running `ApplicationModules.of(...).verify()`. This uses the dependency
already paid for, and would mechanically enforce boundaries this repo currently maintains only by
convention — most concretely, that [ADR-007](ADR-007-ai-assistant.md)'s "AI has zero repository
dependency on decision/scoring/financial" guarantee holds by verified rule, not by nobody having
written that import yet. This is flagged as a good next step, not done as part of Week 12's scope.

## Consequences

**Positive:** the system stays simple to reason about (call a method, read a stack trace) for as
long as that's sufficient — which, at this project's actual scale, is the entire 12-week build.

**Negative:** the unused Modulith dependency is a small amount of unexplained weight in `pom.xml`
until either the events path or the `ApplicationModule`-verification path is actually taken.
