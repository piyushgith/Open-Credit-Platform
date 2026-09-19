package com.opencredit.platform.financial.model;

/**
 * Ratios computed by {@link com.opencredit.platform.financial.support.RatioCalculator} from
 * {@link DerivedFinancialFactCode} values where one exists for the figure, falling back to a raw
 * {@link FinancialLineItemCode} only for atomic figures with no derived composite. Each ratio has
 * one fixed {@link FinancialCategory}.
 */
public enum FinancialRatioCode {
    CURRENT_RATIO(FinancialCategory.LIQUIDITY),
    QUICK_RATIO(FinancialCategory.LIQUIDITY),
    DEBT_TO_EQUITY(FinancialCategory.LEVERAGE),
    DEBT_TO_EBITDA(FinancialCategory.LEVERAGE),
    INTEREST_COVERAGE(FinancialCategory.COVERAGE),
    GROSS_MARGIN(FinancialCategory.PROFITABILITY),
    EBITDA_MARGIN(FinancialCategory.PROFITABILITY),
    NET_MARGIN(FinancialCategory.PROFITABILITY),
    ROA(FinancialCategory.RETURNS),
    ROE(FinancialCategory.RETURNS),
    DSCR(FinancialCategory.COVERAGE);

    private final FinancialCategory category;

    FinancialRatioCode(FinancialCategory category) {
        this.category = category;
    }

    public FinancialCategory getCategory() {
        return category;
    }
}
