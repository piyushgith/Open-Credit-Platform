# Financial Analysis

See [ADR-003](../adr/ADR-003-financial-calculation-engine.md) for the design rationale. This doc is
the operational reference: what gets submitted, what gets computed, in what order.

## Input

`POST /api/loans/{reference}/financial-statements` — a `FinancialPeriod` (label, type: `ACTUAL` /
`AUDITED` / `PROVISIONAL` / `PROJECTED`, start/end date) plus a list of raw `FinancialLineItem`s
(`lineItemCode` + `value`), e.g. `CURRENT_ASSETS`, `REVENUE`, `INTEREST_EXPENSE`. Multiple
statements (different periods) can exist per application — nothing here enforces "latest wins";
each has its own id.

## Analysis

`POST /api/loans/{reference}/financial-statements/{statementId}/analyze` runs one
`FinancialAnalysisRun` through, in this exact order (never skipped, never reordered):

1. **Derived facts** (`DerivedFactCalculator`) — e.g. `totalAssets = currentAssets +
   nonCurrentAssets`, `ebitda`, `workingCapital`. Always computed from child raw/derived values,
   never trusted as a directly-supplied total.
2. **Ratios** (`RatioCalculator`) — `CURRENT_RATIO`, `QUICK_RATIO`, `DEBT_TO_EQUITY`,
   `DEBT_TO_EBITDA`, `INTEREST_COVERAGE`, `GROSS_MARGIN`, `EBITDA_MARGIN`, `NET_MARGIN`, `ROA`,
   `ROE`, `DSCR` — computed only from step 1's derived facts.
3. **Risk indicators** (`RiskIndicatorEvaluator`) — boolean flags:
   `NEGATIVE_CASH_FLOW`, `HIGH_LEVERAGE`, `LOW_LIQUIDITY`, `WEAK_INTEREST_COVERAGE` — computed from
   steps 1-2.

Every stage persists its own rows (`DerivedFinancialFact`, `FinancialRatio`, `RiskIndicator`), all
scoped to the one `FinancialAnalysisRun`. A statement can be re-analyzed (a new run), producing a
new, independently-queryable set — earlier runs aren't deleted or overwritten.

```
GET /api/loans/{reference}/financial-statements                       list statements
GET /api/loans/{reference}/financial-statements/{statementId}
GET /api/loans/{reference}/financial-statements/{statementId}/analysis   list every run for this statement (an array — see below)
```

**Note for API consumers:** `.../analysis` returns a JSON *array* of runs, not a single object —
this tripped up an early draft of `scripts/development/demo.sh`; the fix is in that script's
comments as a concrete reminder.

## Missing values and rounding

A missing input propagates as "this derived fact/ratio/indicator can't be computed" — never
silently defaulted to zero. All arithmetic is `BigDecimal`, with rounding rules defined per
calculation (see the calculators' own tests: `DerivedFactCalculatorTest`, `RatioCalculatorTest`,
`RiskIndicatorEvaluatorTest`) for zero-denominator, negative-value, and multi-period cases.

## Downstream consumers

[credit-scoring.md](credit-scoring.md) reads `FinancialRatio`/`DerivedFinancialFact` rows directly
(cross-module repository read, same pattern documented in
[domain-boundaries.md](../architecture/domain-boundaries.md)); [decisioning.md](decisioning.md)
reads `FinancialRatio`/`RiskIndicator` the same way.
