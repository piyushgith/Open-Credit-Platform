package com.opencredit.platform.authority.exception;

public class ApprovalNotRequiredException extends RuntimeException {

    public ApprovalNotRequiredException(String reason) {
        super("No approval case is required for this decision: " + reason);
    }
}
