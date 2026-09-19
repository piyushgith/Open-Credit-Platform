# Week 6 Implementation Plan — Credit Scoring

## Context

Week 5 (`my_plan/week5-financial-ratios-risk-indicators-plan.md`) shipped `FinancialRatio` and
`RiskIndicator` rows, one set per `FinancialAnalysisRun`. Per [my_docs/plan.md](../my_docs/plan.md)
Week 6, this stage builds a deterministic scorecard engine — `Scorecard`, `ScorecardRule`, `Score`,
`RiskGrade`, `RiskFactor` — that consumes those ratios/indicators (plus two loan-level inputs) and
produces a total score, a risk grade, and a documented, explainable list of contributing/failed
factors. This is a **new module**, `scoring`, not an extension of `financial` — it only reads from
`financial` and `loan` via their existing repositories, the same cross-module pattern
`FinancialStatementService` already uses to read `LoanApplicationRepository`.

## Repository facts this plan depends on

- `FinancialRatioCode` has exactly 11 values; `RiskIndicatorCode` has exactly 6
  (`DECLINING_REVENUE`, `DECLINING_EBITDA`, `NEGATIVE_CASH_FLOW`, `HIGH_LEVERAGE`, `LOW_LIQUIDITY`,
  `WEAK_INTEREST_COVERAGE`). There is **no** revenue-growth percentage anywhere in the financial
  module (`DerivedFinancialFactCode` has no growth fact) — only the boolean `DECLINING_REVENUE`
  risk indicator.
- `Customer` has no income/debt/employment fields, and there is no `CustomerFinancialProfile`
  entity. `LoanApplication.requestDetails` is an untyped JSONB map; for `PersonalLoanRequest` it
  carries `monthlyIncome` and `existingEmi` (both `BigDecimal`), but this is product-specific and
  not indexed/typed at the column level.
- `FinancialAnalysisRun` → `LoanApplication` is an **indirect** link:
  `FinancialAnalysisRun.statementId` → `FinancialStatement.applicationId` → `LoanApplication.id`.
  There is no direct FK from `FinancialAnalysisRun`/`FinancialRatio`/`RiskIndicator` to
  `LoanApplication`.
- Last registered Liquibase changeset is `016`; Week 6 starts at `017`.
- Controller convention: `@RequestMapping("/api/loans/{reference}/<resource>")`, responses wrapped
  in `ApiResponse<T>`, a `POST .../{id}/<verb>` style endpoint triggers a compute-and-persist step
  (see `FinancialStatementController#analyze`).

## Design decisions

| Decision | Choice |
|---|---|
| Revenue-growth factor | Reuse the existing boolean `RiskIndicatorCode.DECLINING_REVENUE` row as-is — no changes to the `financial` module. Confirmed with the user rather than guessed, since the Week 6 example factor list ("Revenue Growth") doesn't match anything that exists today. |
| Customer/loan inputs | In scope for v1: an `EMI_TO_INCOME` factor computed from `LoanApplication.requestDetails` (`existingEmi / monthlyIncome`). Confirmed with the user. Resolved generically by key lookup (`monthlyIncome`, `existingEmi`) rather than gating on `productType == PERSONAL`, so it applies to any product whose `requestDetails` happens to carry those keys and is silently skipped (not defaulted to zero) otherwise — same missing-propagates rule as Weeks 4–5. |
| Scoring factors (v1) | `DEBT_TO_EBITDA`, `INTEREST_COVERAGE`, `DSCR`, `CURRENT_RATIO` (from `FinancialRatio`), `DECLINING_REVENUE` (from `RiskIndicator`, as 1/0), `EMI_TO_INCOME` (from loan JSONB) — a new `ScoreFactorCode` enum, distinct from `FinancialRatioCode`/`RiskIndicatorCode` since it spans multiple sources. |
| "Configurable scorecard rules" without a rule engine | Every factor reduces to one `BigDecimal` input value. A `ScorecardRule` is a **numeric band**: `(scorecardId, factorCode, bandOrder, minValue?, maxValue?, points)`, `min`/`max` nullable meaning unbounded. Scoring = look up which band contains the input value, add its `points`. No expression language, no reflection — just range comparison, matching the project's "no generic expression language unless required" rule. The boolean `DECLINING_REVENUE` indicator is encoded as `1`/`0` so it fits the same band mechanism instead of a special case. |
| Score envelope is also data, not code | `Scorecard` carries `baseScore`, `minScore`, `maxScore` columns. `totalScore = clamp(baseScore + sum(matched band points), minScore, maxScore)`. Only one `Scorecard` is `active` at a time (mirrors the existing "only one active X" pattern from `UnderwritingCase`/`LoanDocument`); `ScorecardRepository.findByActiveTrue()` selects it. |
| Risk grade boundaries | Fixed thresholds on a plain `RiskGrade` enum (`A >= 750`, `B >= 700`, `C >= 650`, `D >= 600`, else `F`), **not** a configurable table — same "plain enum, not its own table" precedent Week 5 used for `FinancialCategory`. Only the factor bands are configurable per the spec; grade cut-offs are a fixed, documented policy. |
| Missing inputs | A factor whose input can't be resolved (ratio not persisted for that run, e.g. zero-denominator; `requestDetails` missing the expected keys) is skipped entirely — no `RiskFactor` row, no contribution to the score. This is the same "absence over defaulting" rule as Weeks 4–5, called out explicitly since it means two applications can have different sets of evaluated factors and therefore aren't perfectly comparable — a documented simplification, not a bug. |
| Factor labels | Each `ScoreFactorCode` carries a fixed pair of human-readable labels (`riskLabel`, `strengthLabel`), e.g. `DEBT_TO_EBITDA` → ("High leverage", "Low leverage") — mirrors `FinancialRatioCode.category()` carrying small fixed metadata on the enum. A band with positive points uses `strengthLabel`, negative points uses `riskLabel`, zero points emits no label (persisted but excluded from both response lists). |
| One score per (run, scorecard) | `score` has a unique `(analysis_run_id, scorecard_id)` constraint. Re-scoring the same run under the same active scorecard throws `DuplicateScoreException` (mirrors `DuplicateCustomerException`/`DuplicateKycCaseException`) — the client re-fetches the existing score via `GET`. Re-scoring is only possible by activating a different `Scorecard` row. |
| Architecture | New module `scoring`, sibling to `financial`/`loan`. One pure, stateless `@Component` (`ScoreCalculator`, mirrors `RatioCalculator`/`RiskIndicatorEvaluator`) does only the band-matching/summation math. `ScoreService` (the transactional boundary) resolves all inputs — reads `FinancialRatioRepository`/`RiskIndicatorRepository`/`FinancialStatementRepository` (financial) and `LoanApplicationRepository` (loan) directly, same cross-module-repository pattern already used by `FinancialStatementService`. |

## Schema changes (Liquibase, additive changesets)

- `017-create-scorecard.sql`: `scorecard` — `id`, `name` (unique), `active BOOLEAN`, `base_score INT`, `min_score INT`, `max_score INT`, `created_at`.
- `018-create-scorecard-rule.sql`: `scorecard_rule` — `id`, `scorecard_id` (FK), `factor_code`, `band_order`, `min_value NUMERIC(19,4)` (nullable), `max_value NUMERIC(19,4)` (nullable), `points INT`, `created_at`; unique `(scorecard_id, factor_code, band_order)`.
- `019-create-score.sql`: `score` — `id`, `analysis_run_id` (FK), `scorecard_id` (FK), `total_score INT`, `risk_grade VARCHAR`, `created_at`; unique `(analysis_run_id, scorecard_id)`.
- `020-create-risk-factor.sql`: `risk_factor` — `id`, `score_id` (FK), `factor_code`, `value NUMERIC(19,4)`, `points INT`, `description VARCHAR(200)`, `created_at`.
- `021-seed-default-scorecard.sql`: one seeded `scorecard` (`SME_STANDARD_V1`, `active=true`, `base_score=500`, `min_score=300`, `max_score=900`) plus its `scorecard_rule` bands for all six `ScoreFactorCode` values, documented inline as SQL comments (formula/threshold source = this plan's design table).

## Module: new `scoring` module

- `model`: `ScoreFactorCode` enum (with `riskLabel()`/`strengthLabel()`), `RiskGrade` enum (with `fromScore(int)`), `Scorecard`, `ScorecardRule`, `Score`, `RiskFactor` entities.
- `repository`: `ScorecardRepository` (`findByActiveTrue`), `ScorecardRuleRepository` (`findAllByScorecardId`), `ScoreRepository` (`findAllByAnalysisRunId`, `existsByAnalysisRunIdAndScorecardId`), `RiskFactorRepository` (`findAllByScoreId`).
- `support.ScoreCalculator`: pure `@Component`; `calculate(Map<ScoreFactorCode, BigDecimal> inputs, Map<ScoreFactorCode, List<ScorecardRule>> rulesByFactor, Scorecard scorecard)` → a small result record carrying `totalScore`, `riskGrade`, and one outcome per evaluated factor (`factorCode`, `value`, `points`, `description`).
- `dto`: `ScoreResponse` (`id`, `scorecardName`, `totalScore`, `riskGrade`, `contributingFactors`, `failedFactors`, `createdAt`), `RiskFactorResponse` (`factorCode`, `value`, `points`, `description`).
- `exception`: `NoActiveScorecardException`, `DuplicateScoreException`, `ScoreNotFoundException`.
- `ScoreService` (`@Service @Transactional`): `score(reference, analysisRunId)` — loads the `FinancialAnalysisRun` and its `FinancialRatio`/`RiskIndicator` rows, walks `FinancialStatement.applicationId` to load the `LoanApplication` for `requestDetails`, loads the active `Scorecard` + its rules, builds the `Map<ScoreFactorCode, BigDecimal>` input map (skipping unresolvable factors), calls `ScoreCalculator`, persists `Score` + `RiskFactor` rows, returns `ScoreResponse`. Also `list(reference, analysisRunId)` / `get(reference, scoreId)`.
- `ScoringController`: `@RequestMapping("/api/loans/{reference}/financial-statements/{statementId}/analysis-runs/{analysisRunId}/score")` — `POST` triggers scoring, `GET` returns the existing score(s) for that run — kept nested under the existing financial-statement path since a score is meaningless without its analysis run, same nesting `FinancialStatementController` already uses for `/analysis`.

## Tests

- `ScoreCalculatorTest` (pure) — each `ScoreFactorCode` band boundary (inclusive/exclusive edges) produces the right points; the boolean `DECLINING_REVENUE` factor as `1`/`0`; a factor with no matching band is skipped; missing inputs skip only that factor while others still score; `totalScore` clamps at `minScore`/`maxScore`; `RiskGrade` boundary values (`599/600`, `649/650`, `699/700`, `749/750`); deterministic repeatability (same inputs twice → identical output).
- `ScoringIntegrationTest` (new, or extend `LoanApplicationIntegrationTest`'s financial flow) — submit + analyze a statement, then `POST .../score` returns a persisted score with the expected grade; scoring the same run again under the same scorecard returns `DuplicateScoreException`/409; a `PersonalLoanRequest` with `monthlyIncome`/`existingEmi` produces an `EMI_TO_INCOME` factor, a non-personal product without those keys does not (and scoring still succeeds without it).

## Out of scope (later weeks)

Credit policy rules and the `APPROVE`/`REFER`/`DECLINE` decision engine (Week 7 — Credit Rules +
Decision, which consumes this week's `Score`/`RiskGrade` as an input). Configurable risk-grade
boundaries, a real revenue-growth fact (would require extending `financial`'s
`DerivedFinancialFactCode`, deferred per the user's decision above), multiple scorecards active
per product/segment simultaneously, scorecard versioning/effective-dating beyond a single
`active` flag.
