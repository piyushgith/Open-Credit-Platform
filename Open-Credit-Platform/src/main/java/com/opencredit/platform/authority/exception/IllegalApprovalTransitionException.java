package com.opencredit.platform.authority.exception;

import com.opencredit.platform.authority.model.ApprovalCaseStatus;
import com.opencredit.platform.authority.model.ApprovalRole;

public class IllegalApprovalTransitionException extends RuntimeException {

    public IllegalApprovalTransitionException(ApprovalCaseStatus status, ApprovalRole attemptedRole) {
        super(attemptedRole + " cannot act on an approval case in status " + status);
    }
}
