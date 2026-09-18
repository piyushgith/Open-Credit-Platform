package com.opencredit.platform.loan.exception;

import com.opencredit.platform.loan.ProductType;

/**
 * Thrown when a {@link ProductType} is recognized by the DTO layer (it deserialized
 * successfully) but no {@code LoanProcessingStrategy} bean is registered for it.
 * This is a wiring gap, not a client error caused by malformed input.
 */
public class UnsupportedProductTypeException extends RuntimeException {

    private final ProductType productType;

    public UnsupportedProductTypeException(ProductType productType) {
        super("No loan processing strategy registered for product type: " + productType);
        this.productType = productType;
    }

    public ProductType getProductType() {
        return productType;
    }
}
