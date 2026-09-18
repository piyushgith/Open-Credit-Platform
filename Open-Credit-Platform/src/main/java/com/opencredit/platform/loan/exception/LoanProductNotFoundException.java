package com.opencredit.platform.loan.exception;

import com.opencredit.platform.loan.ProductType;

public class LoanProductNotFoundException extends RuntimeException {

    public LoanProductNotFoundException(ProductType productType) {
        super("No active loan product found for product type: " + productType);
    }
}
