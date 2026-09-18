package com.opencredit.platform.financial.model;

/**
 * Raw, source-supplied financial facts. Deliberately excludes composite figures (e.g. total
 * assets) so a total can only ever come from {@link DerivedFinancialFactCode} calculation,
 * never from a caller-supplied value.
 */
public enum FinancialLineItemCode {
    CURRENT_ASSETS,
    NON_CURRENT_ASSETS,
    CURRENT_LIABILITIES,
    NON_CURRENT_LIABILITIES,
    EQUITY,
    REVENUE,
    COST_OF_GOODS_SOLD,
    OPERATING_EXPENSES,
    DEPRECIATION_AMORTIZATION,
    INTEREST_EXPENSE,
    TAX_EXPENSE,
    LONG_TERM_DEBT,
    SHORT_TERM_DEBT
}
