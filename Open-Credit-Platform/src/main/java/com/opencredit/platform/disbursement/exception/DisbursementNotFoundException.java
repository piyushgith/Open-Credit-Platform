package com.opencredit.platform.disbursement.exception;

public class DisbursementNotFoundException extends RuntimeException {

    public DisbursementNotFoundException(String referenceNumber) {
        super("No disbursement has been recorded yet for application: " + referenceNumber);
    }
}
