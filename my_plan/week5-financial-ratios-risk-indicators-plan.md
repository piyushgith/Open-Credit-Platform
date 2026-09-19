# Week 5 Implementation Plan — Financial Ratios + Risk Indicators

## Context

Week 4 (`my_plan/week4-financial-statement-engine-plan.md`) shipped the financial analysis engine
through step 4 of the processing order (extract → normalize → derive → persist derived facts),
deferring ratios/categories to Week 5 as its own plan documented. Per
[my_docs/plan.md](../my_docs/plan.md) Week 5, this stage extends that engine with financial
ratios, financial categories, and risk indicators — steps 5-6 of the original 7-step order, plus
a new risk-indicator step not covered by Week 4's `DerivedFactCalculator`.

## Design decisions

| Decision | Choice |
|---|---|
| Ratios computed (Week 5) | `CURRENT_RATIO`, `QUICK_RATIO`, `DEBT_TO_EQUITY`, `DEBT_TO_EBITDA`, `INTEREST_COVERAGE`, `GROSS_MARGIN`, `EBITDA_MARGIN`, `NET_MARGIN`, `ROA`, `ROE`, `DSCR` — exactly the Week 5 prompt's list |
| Ratio inputs | Every ratio reads a `DerivedFinancialFactCode` when one exists for that figure (`TOTAL_ASSETS`, `TOTAL_DEBT`, `GROSS_PROFIT`, `EBITDA`, `NET_PROFIT`); it reads a raw `FinancialLineItemCode` only for atomic figures that have no derived composite (`CURRENT_ASSETS`, `CURRENT_LIABILITIES`, `EQUITY`, `REVENUE`, `INTEREST_EXPENSE`, `SHORT_TERM_DEBT`, `INVENTORY`) — same "never bypass a derived fact" rule Week 4 established |
| New raw line item | `INVENTORY` added to `FinancialLineItemCode` — required for `QUICK_RATIO`; the column is a plain `VARCHAR(40)` with no DB check constraint (validated by Jackson enum deserialization), so this is a Java-only, additive change, no new changeset |
| `FinancialCategory` | A plain enum (`LIQUIDITY`, `LEVERAGE`, `PROFITABILITY`, `COVERAGE`, `RETURNS`), not its own table — mirrors how `FinancialPeriodType` is an enum embedded on `FinancialPeriod`, not a separate entity. Each `FinancialRatioCode` has one fixed category (`FinancialRatioCode.category()`); "calculating financial categories" (processing-order step 6) means tagging each computed ratio row with its category, not producing a new numeric value |
| DSCR formula | `EBITDA / (INTEREST_EXPENSE + SHORT_TERM_DEBT)` — the schema has no "current portion of long-term debt" or debt-service-schedule concept, so `SHORT_TERM_DEBT` is used as the documented proxy for the current-period principal obligation. Called out explicitly here and in the calculator's Javadoc since it's a simplification, not textbook DSCR |
| Zero denominators | A ratio whose denominator is zero is **not computed** (no row persisted) — same "absence over defaulting/throwing" rule Week 4 used for missing inputs, extended to the new zero-denominator case Week 4's plan explicitly deferred |
| Ratio precision/rounding | `BigDecimal`, scale 4, `RoundingMode.HALF_UP` (`NUMERIC(19,4)`) — deliberately more precise than the money scale (2) used for line items/derived facts, since ratios are dimensionless, not currency |
| Risk indicators computed | `DECLINING_REVENUE`, `DECLINING_EBITDA`, `NEGATIVE_CASH_FLOW`, `HIGH_LEVERAGE`, `LOW_LIQUIDITY`, `WEAK_INTEREST_COVERAGE` — exactly the Week 5 prompt's list |
| Risk indicator thresholds | `HIGH_LEVERAGE`: `DEBT_TO_EQUITY > 2.00`. `LOW_LIQUIDITY`: `CURRENT_RATIO < 1.00`. `WEAK_INTEREST_COVERAGE`: `INTEREST_COVERAGE < 1.50`. `NEGATIVE_CASH_FLOW`: `NET_PROFIT + DEPRECIATION_AMORTIZATION < 0` (operating-cash-flow proxy — no cash flow statement is modeled). All four reuse an already-computed ratio/fact rather than re-deriving raw values |
| Period-over-period indicators | `DECLINING_REVENUE`/`DECLINING_EBITDA` compare the current statement's `REVENUE` line item / `EBITDA` fact against the immediately preceding period for the *same loan application* (previous `FinancialStatement` with the latest `endDate` strictly before the current period's `endDate`). If there is no earlier statement, or the earlier statement was never analyzed (no `EBITDA` fact yet), the corresponding indicator is skipped — same missing-propagates rule |
| Risk indicator persistence | One row per **evaluable** indicator (inputs present), storing a `triggered` boolean — not just triggered ones — so a run's audit trail shows every check that ran and its outcome, matching the "never overwrite, append" run auditability from Week 4 |
| Architecture | Two new pure, stateless `@Component`s — `RatioCalculator` and `RiskIndicatorEvaluator` — mirroring `DerivedFactCalculator`, wired into the existing `FinancialStatementService.analyze()` method rather than a new orchestration service. `analyze()` gains one private helper (`previousPeriodComparison`) to look up the prior statement; no new controller endpoints, no new top-level service |

## Schema changes (Liquibase, additive changesets)

- `015-create-financial-ratio.sql`, `016-create-risk-indicator.sql`.
- `financial_ratio`: `id`, `analysis_run_id` (FK), `ratio_code`, `category`, `value NUMERIC(19,4)`, `created_at`; unique `(analysis_run_id, ratio_code)`.
- `risk_indicator`: `id`, `analysis_run_id` (FK), `indicator_code`, `triggered BOOLEAN`, `created_at`; unique `(analysis_run_id, indicator_code)`.
- No changeset needed for `INVENTORY` (enum-only addition to the existing `VARCHAR` column).

## Module changes: `financial`

- `model`: new `FinancialRatioCode`, `FinancialCategory`, `RiskIndicatorCode` enums; new `FinancialRatio`, `RiskIndicator` entities; `INVENTORY` added to `FinancialLineItemCode`.
- `repository`: `FinancialRatioRepository`, `RiskIndicatorRepository` (`findAllByAnalysisRunId`).
- `support.RatioCalculator`: pure `@Component`; `calculate(rawValues, derivedFacts)` → `Map<FinancialRatioCode, BigDecimal>`.
- `support.RiskIndicatorEvaluator`: pure `@Component`; `evaluate(ratios, facts, previousPeriod)` → `Map<RiskIndicatorCode, Boolean>`, where `previousPeriod` is a small record `PreviousPeriodFacts(Optional<BigDecimal> revenue, Optional<BigDecimal> ebitda)`.
- `dto`: `FinancialRatioResponse` (`id`, `ratioCode`, `category`, `value`), `RiskIndicatorResponse` (`id`, `indicatorCode`, `triggered`); `FinancialAnalysisRunResponse` gains `ratios` and `riskIndicators` lists alongside the existing `facts`.
- `FinancialStatementService.analyze()`: after computing derived facts, resolves the previous statement/period (if any), calls both new calculators, and persists `FinancialRatio`/`RiskIndicator` rows tied to the same `FinancialAnalysisRun` — one run, three kinds of child rows, still one transaction.

## Tests

- `RatioCalculatorTest` (pure) — all eleven ratios with full inputs; zero-denominator cases skip only that ratio; missing derived/raw inputs skip only dependent ratios; negative numerator/denominator combinations; rounding at scale 4 `HALF_UP`.
- `RiskIndicatorEvaluatorTest` (pure) — each of the six indicators triggers/doesn't at its threshold boundary; `DECLINING_REVENUE`/`DECLINING_EBITDA` with and without a previous period; missing inputs skip only that indicator (others still evaluate).
- Extend `LoanApplicationIntegrationTest`'s financial-statement test (and/or add a new one) — analyzing a single statement (no prior period) persists ratios/categories and skips the two comparative indicators; submitting and analyzing a second, later-period statement for the same application triggers `DECLINING_REVENUE`/`DECLINING_EBITDA` correctly.

## Out of scope (later weeks)

Scoring/scorecards, credit policy rules, risk grades (Week 6 — Credit Scoring, which has its own
`RiskFactor`/`RiskGrade` entities distinct from this week's `RiskIndicator`). A real cash-flow
statement, a debt-service schedule (for a non-proxy DSCR), multi-currency, statement amendment.
