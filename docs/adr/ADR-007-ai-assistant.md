# ADR-007: AI Credit Assistant Boundary

**Status:** Proposed — not yet implemented. There is no `ai` package in this repository as of Week
12; this ADR records the intended design from `my_plan/week11-ai-credit-assistant-plan.md` so the
boundary is decided *before* implementation, not discovered afterward.

## Context

`my_docs/plan.md`'s framing: AI is an assistant, not the decision maker.
`Deterministic Rules + Scorecard -> Credit Decision -> AI Assistant -> Explanation`. The risk to
design against is AI output quietly influencing or overriding the deterministic outcome computed by
[ADR-004](ADR-004-credit-decision-engine.md)'s engine.

## Decision (proposed)

A new `ai` module that depends only on `decision`/`scoring`/`financial`'s **response DTOs**
(`CreditDecisionResponse`, `ScoreResponse`, `FinancialRatioResponse`, `RiskIndicatorResponse`) —
the same objects those modules already serialize for their own REST APIs — with **zero dependency
on their repositories**. Structurally, this means no code path can exist for AI output to be
written into `CreditDecision`, `Score`, `ApprovalCase`, or `LoanApplication.status`, because the
`ai` module never holds a reference to any of those repositories.

Three fixed, closed task types (`EXPLAIN_DECISION`, `SUGGEST_QUESTIONS`, `DRAFT_CREDIT_MEMO`) rather
than either six bespoke endpoints or one fully generic "ask anything" endpoint. A thin `WebClient`-
based client (already a dependency) calls the Anthropic Messages API directly — not `spring-ai`,
which would add a multi-provider abstraction this single-provider project has no use for.

## Consequences (anticipated)

**Positive:** the "AI never overrides a decision" guarantee is checkable by construction and by a
straightforward integration test (re-read `CreditDecision` before/after calling the assist
endpoint, assert it's unchanged) rather than resting on convention alone.

**Negative:** anomaly identification (one of `my_docs/plan.md`'s six listed AI capabilities) is
explicitly out of scope for the first implementation — it needs cross-run trend data no existing
DTO assembles yet. See `my_plan/week11-ai-credit-assistant-plan.md` for the full design, including
failure-mode handling (`AiUnavailableException`, `AiResponseParseException`) and persistence design.

## Revisit

Once implemented, add `@ApplicationModule` boundary annotations (see
[ADR-008](ADR-008-event-driven-evolution.md)) so this "no repository dependency" guarantee is
mechanically verified by a `spring-modulith` `ApplicationModules.verify()` test, not just
maintained by discipline.
