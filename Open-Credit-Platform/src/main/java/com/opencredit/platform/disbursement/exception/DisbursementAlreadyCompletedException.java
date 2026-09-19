package com.opencredit.platform.disbursement.exception;

import java.util.UUID;

public class DisbursementAlreadyCompletedException extends RuntimeException {

    public DisbursementAlreadyCompletedException(UUID applicationId) {
        super("Application " + applicationId + " has already been fully disbursed");
    }
}
