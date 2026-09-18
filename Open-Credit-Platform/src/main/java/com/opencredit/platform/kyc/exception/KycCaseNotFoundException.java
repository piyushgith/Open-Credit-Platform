package com.opencredit.platform.kyc.exception;

public class KycCaseNotFoundException extends RuntimeException {

    public KycCaseNotFoundException(String referenceNumber) {
        super("No KYC case found for loan application: " + referenceNumber);
    }
}
