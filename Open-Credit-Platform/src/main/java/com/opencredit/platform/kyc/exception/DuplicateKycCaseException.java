package com.opencredit.platform.kyc.exception;

public class DuplicateKycCaseException extends RuntimeException {

    public DuplicateKycCaseException(String referenceNumber) {
        super("A KYC case already exists for loan application: " + referenceNumber);
    }
}
