package com.opencredit.platform.disbursement.exception;

public class DuplicateDisbursementRequestException extends RuntimeException {

    public DuplicateDisbursementRequestException(String requestReference) {
        super("A disbursement tranche with requestReference '" + requestReference + "' has already been recorded");
    }
}
