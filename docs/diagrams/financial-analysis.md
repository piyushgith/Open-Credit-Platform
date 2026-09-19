# Financial Analysis Pipeline

`my_docs/plan.md` §5 names this `financial-analysis.png`. See
[domain/financial-analysis.md](../domain/financial-analysis.md) and
[ADR-003](../adr/ADR-003-financial-calculation-engine.md) for the full rationale.

```mermaid
flowchart LR
    Raw["Raw line items<br/>(FinancialLineItem, persisted as submitted)"]
    Derived["Derived facts<br/>(DerivedFactCalculator)<br/>GROSS_PROFIT -> OPERATING_PROFIT -> EBITDA/NET_PROFIT"]
    Ratios["Ratios<br/>(RatioCalculator)<br/>11 FinancialRatioCode values"]
    Risk["Risk indicators<br/>(RiskIndicatorEvaluator)<br/>6 RiskIndicatorCode checks"]
    Persist["FinancialAnalysisRun<br/>(all of the above, persisted together)"]

    Raw --> Derived --> Ratios --> Risk --> Persist
    Raw -.->|atomic figures with no derived composite\n(current assets/liabilities, equity, revenue, ...)| Ratios
```

Every stage never defaults a missing input to zero — an unresolvable fact/ratio/indicator is simply
left out of the result, not silently computed as if the missing value were `0`. A statement can be
re-analyzed (a new `FinancialAnalysisRun`); earlier runs are never overwritten, only superseded.
