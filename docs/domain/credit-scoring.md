# Credit Scoring

`scoring/support/ScoreCalculator` is a pure, stateless component (mirrors `financial`'s
calculators): given resolved financial factors and a `Scorecard`'s configured `ScorecardRule`s, it
produces a total score, `RiskGrade` (`A`-`F`), and a `RiskFactor` row for every evaluated factor
(both contributing and failed — same "persist everything, not just failures" precedent as
`decision`'s `RuleResult`).

## Scorecards

`Scorecard` (name, min/max score range) has many `ScorecardRule`s, each mapping one factor
(`DEBT_TO_EBITDA`, `INTEREST_COVERAGE`, `DSCR`, `CURRENT_RATIO`, `EMI_TO_INCOME`, ...) into
point-scoring bands. A partial unique index enforces **exactly one active scorecard at a time**
(`uq_scorecard_active ON scorecard(active) WHERE active`) — activating a new one is expected to
deactivate the previous one in the same operation (`ScorecardAdminService`).

```
POST /api/scorecards                       admin only (@PreAuthorize hasRole ADMIN)
GET  /api/scorecards
PUT  /api/scorecards/{id}
DELETE /api/scorecards/{id}                 rejected if the scorecard is active or already used to score a run
POST /api/scorecards/{id}/rules
```

## Producing a Score

```
POST /api/loans/{reference}/financial-statements/{statementId}/analysis-runs/{analysisRunId}/score
GET  /api/loans/{reference}/financial-statements/{statementId}/analysis-runs/{analysisRunId}/score
```

Reads the active scorecard and the target `FinancialAnalysisRun`'s persisted
`FinancialRatio`/`DerivedFinancialFact` rows directly. `UNIQUE (analysis_run_id, scorecard_id)` —
scoring the same run under the same scorecard twice is rejected (`DuplicateScoreException`), not
silently overwritten; scoring under a newly-activated scorecard produces a new `Score` row.

A factor with no resolvable input is skipped from evaluation entirely, same as
[the decision engine](decisioning.md) — never defaulted to a neutral/zero score.

## Where this feeds

[decisioning.md](decisioning.md)'s `CreditDecision` is computed against a `Score`, and
[../architecture/domain-boundaries.md](../architecture/domain-boundaries.md) documents that read as
a direct cross-module repository call, not an event or a shared service call.
