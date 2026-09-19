package com.opencredit.platform.financial.model;

/**
 * Boolean risk checks computed by
 * {@link com.opencredit.platform.financial.support.RiskIndicatorEvaluator}. {@code DECLINING_*}
 * indicators compare against the previous financial period for the same loan application; the
 * rest evaluate a single-period ratio/fact against a fixed threshold.
 */
public enum RiskIndicatorCode {
    DECLINING_REVENUE,
    DECLINING_EBITDA,
    NEGATIVE_CASH_FLOW,
    HIGH_LEVERAGE,
    LOW_LIQUIDITY,
    WEAK_INTEREST_COVERAGE
}
