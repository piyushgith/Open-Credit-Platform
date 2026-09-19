package com.opencredit.platform.disbursement.exception;

import java.util.UUID;

public class ConcurrentDisbursementConflictException extends RuntimeException {

    public ConcurrentDisbursementConflictException(UUID applicationId) {
        super("Another disbursement tranche was recorded concurrently for application: " + applicationId);
    }
}
