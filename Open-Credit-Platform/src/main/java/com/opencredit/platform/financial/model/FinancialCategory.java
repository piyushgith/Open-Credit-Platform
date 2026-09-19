package com.opencredit.platform.financial.model;

/**
 * Groups a {@link FinancialRatioCode} for reporting/filtering. Assignment is fixed per ratio —
 * see {@link FinancialRatioCode#getCategory()}.
 */
public enum FinancialCategory {
    LIQUIDITY,
    LEVERAGE,
    PROFITABILITY,
    COVERAGE,
    RETURNS
}
