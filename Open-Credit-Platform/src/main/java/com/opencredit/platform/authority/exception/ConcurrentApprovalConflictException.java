package com.opencredit.platform.authority.exception;

import com.opencredit.platform.authority.model.ApprovalRole;

public class ConcurrentApprovalConflictException extends RuntimeException {

    public ConcurrentApprovalConflictException(ApprovalRole role) {
        super("Another " + role + " decision was recorded concurrently on this approval case");
    }
}
