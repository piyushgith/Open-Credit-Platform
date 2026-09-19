# ADR-004: Credit Decision Engine

**Status:** Accepted (Week 6-7)

## Context

A credit decision must be deterministic and explainable — the same score, under the same policy,
must always produce the same outcome, and the reason for a decline or referral must be a plain
description an analyst can act on, not a black box. `my_docs/plan.md`'s non-negotiable principle
#18/#19: financial decisions must be deterministic; AI must never make or override one (relevant
once Week 11 lands — see [ADR-007](ADR-007-ai-assistant.md)).

## Decision

Two-stage, both deterministic:

1. **Scoring** (`scoring` module): `ScoreCalculator` evaluates a configurable `Scorecard`'s rules
   against resolved financial factors, producing a `Score`/`RiskGrade` plus `RiskFactor` rows for
   every contributing/failed factor.
2. **Decisioning** (`decision` module): `DecisionEngine.evaluate` evaluates a configurable
   `CreditPolicy`'s `CreditRule`s against the score's resolved factors. A rule's set is implicitly
   ANDed: any failed `HARD` rule forces `DECLINE`; a failed `SOFT` rule (with all `HARD` rules
   passing) forces `REFER`; all rules passing approves. A rule whose input is unresolvable is simply
   skipped — never defaulted. Zero evaluable rules refers rather than approves (deterministic
   approval requires positive evidence, not merely an absence of evidence).

Neither engine uses a generic rules-engine framework or expression language — `RuleCondition`/
`RuleOutcome` are plain records, and the "engine" is a `@Component` with one `evaluate` method, per
`my_docs/plan.md`'s "no generic framework-building" principle. A `RuleResult` row is persisted for
*every* evaluated rule (passed and failed), not just failures — that full row set *is* "decision
reason," per `RuleResult`'s own Javadoc, rather than a separately modeled concept.

## Consequences

**Positive:** `CreditDecisionResponse.failedRules`/`passedRules` already carries a complete,
human-readable explanation with every decision — no extra work needed to explain a decision after
the fact (and no extra work needed to feed Week 11's AI assistant structured facts once built; see
`my_plan/week11-ai-credit-assistant-plan.md`).

**Negative:** through Week 9, this decision-engine track did not drive `LoanApplication.status` at
all, so a `CreditDecision` — however correct and explainable — never actually produced an Offer.
Week 12 closed that gap (see the architecture overview's "Two decision tracks, one lifecycle owner"
section): `authority.ApprovalService` now finalizes the application once this engine's outcome (and,
where required, a maker-checker sign-off) is settled. The two decision tracks (this one, and the
simpler FOIR-based `LoanProcessingStrategy` from Week 2) still don't compose on a single
application — that reconciliation remains a real, open design question, just no longer one where
the "sophisticated" track's output goes nowhere regardless of the answer.
