package com.opencredit.platform.loan;

/**
 * Supported loan products. Adding a new value here plus a matching
 * {@code @JsonSubTypes.Type} on {@link com.opencredit.platform.loan.dto.LoanRequest} /
 * {@link com.opencredit.platform.loan.dto.LoanResponse} and a
 * {@link com.opencredit.platform.loan.strategy.LoanProcessingStrategy} bean is the
 * complete set of changes needed to onboard a product.
 */
public enum ProductType {
    PERSONAL,
    VEHICLE
}
