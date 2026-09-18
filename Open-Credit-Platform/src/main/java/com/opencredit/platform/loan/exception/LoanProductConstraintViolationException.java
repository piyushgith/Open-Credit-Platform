package com.opencredit.platform.loan.exception;

/**
 * Thrown when a requested amount or tenure falls outside the matched
 * {@link com.opencredit.platform.loan.model.LoanProduct}'s bounds.
 */
public class LoanProductConstraintViolationException extends RuntimeException {

    public LoanProductConstraintViolationException(String message) {
        super(message);
    }
}
