package com.opencredit.platform.loan.exception;

public class LoanApplicationNotFoundException extends RuntimeException {

    public LoanApplicationNotFoundException(String reference) {
        super("No loan application found with reference: " + reference);
    }
}
