package com.opencredit.platform.financial.model;

/**
 * Facts computed by {@link com.opencredit.platform.financial.support.DerivedFactCalculator}.
 * {@code OPERATING_PROFIT} depends on {@code GROSS_PROFIT}; {@code EBITDA} and {@code NET_PROFIT}
 * depend on {@code OPERATING_PROFIT} — a genuine two-level dependency chain, not just raw sums.
 */
public enum DerivedFinancialFactCode {
    TOTAL_ASSETS,
    TOTAL_LIABILITIES,
    WORKING_CAPITAL,
    TOTAL_DEBT,
    GROSS_PROFIT,
    OPERATING_PROFIT,
    EBITDA,
    NET_PROFIT
}
