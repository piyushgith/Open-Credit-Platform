package com.opencredit.platform.authority.exception;

import com.opencredit.platform.authority.model.ApprovalLevel;

public class InsufficientApprovalAuthorityException extends RuntimeException {

    public InsufficientApprovalAuthorityException(ApprovalLevel actorLevel, ApprovalLevel requiredLevel) {
        super("Checker authority " + actorLevel + " is insufficient; this case requires at least " + requiredLevel);
    }
}
