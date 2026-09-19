package com.opencredit.platform.decision.model;

/**
 * The inputs a {@link CreditRule} can test: the score envelope itself ({@code TOTAL_SCORE},
 * {@code RISK_GRADE} — read from {@code Score}), every {@code FinancialRatioCode} (read from
 * {@code FinancialRatio}), and every {@code RiskIndicatorCode} encoded as {@code 1}/{@code 0}
 * (read from {@code RiskIndicator}) — both keyed by the same {@code FinancialAnalysisRun}. A new
 * spanning enum, distinct from those three, since it spans multiple sources — same precedent
 * {@code ScoreFactorCode} set in Week 6. {@code RISK_GRADE} is compared as an ordinal:
 * {@code A=5, B=4, C=3, D=2, F=1} (higher is better). {@code EMI_TO_INCOME} is deliberately not
 * included here — it already feeds the score itself, so re-testing it in policy rules would be
 * duplicate business logic against the same raw fact.
 */
public enum CreditRuleFactorCode {
    TOTAL_SCORE,
    RISK_GRADE,
    CURRENT_RATIO,
    QUICK_RATIO,
    DEBT_TO_EQUITY,
    DEBT_TO_EBITDA,
    INTEREST_COVERAGE,
    GROSS_MARGIN,
    EBITDA_MARGIN,
    NET_MARGIN,
    ROA,
    ROE,
    DSCR,
    DECLINING_REVENUE,
    DECLINING_EBITDA,
    NEGATIVE_CASH_FLOW,
    HIGH_LEVERAGE,
    LOW_LIQUIDITY,
    WEAK_INTEREST_COVERAGE
}
