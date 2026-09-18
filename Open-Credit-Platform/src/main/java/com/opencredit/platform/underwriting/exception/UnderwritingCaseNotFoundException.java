package com.opencredit.platform.underwriting.exception;

public class UnderwritingCaseNotFoundException extends RuntimeException {

    public UnderwritingCaseNotFoundException(String referenceNumber) {
        super("No underwriting case found for loan application: " + referenceNumber);
    }
}
