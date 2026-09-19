# ADR-003: Financial Calculation Engine

**Status:** Accepted (Week 4-5)

## Context

Financial ratios and risk indicators must be reproducible and auditable years later, not just
"whatever the spreadsheet said at upload time." A naive design would let ratios read raw uploaded
line items directly, letting a bad or manipulated input silently produce a wrong ratio with no
trace of how it was derived.

## Decision

A strict processing order, enforced by `financial/support/DerivedFactCalculator` ->
`RatioCalculator` -> `RiskIndicatorEvaluator`, each a pure, stateless component:

```
Raw FinancialLineItem (persisted, never mutated)
    -> DerivedFinancialFact (e.g. totalAssets = currentAssets + nonCurrentAssets — always
       recomputed from child values, never trusted as a supplied total)
    -> FinancialRatio (computed only from persisted DerivedFinancialFacts)
    -> RiskIndicator (computed only from persisted FinancialRatios/DerivedFinancialFacts)
```

`BigDecimal` throughout, with explicit rounding rules. A missing input propagates as "this
ratio/rule can't be evaluated" — never silently defaulted to zero. Every stage's output is
persisted (`DerivedFinancialFact`, `FinancialRatio`, `RiskIndicator` all have their own tables), so
a later audit can trace any ratio back through its derived facts to the original raw line items.

## Consequences

**Positive:** a ratio is always traceable to the raw data it came from; recomputing it later (e.g.
after an audit question) reproduces exactly the same value, since nothing in the chain depends on
external/mutable state. Zero-denominator and missing-value cases are explicit code paths with
tests, not accidental `NaN`/exception surprises.

**Negative:** adding a new ratio or derived fact means touching three layers (fact, ratio,
potentially a risk indicator) rather than one formula — a deliberate cost in exchange for the
traceability guarantee.
