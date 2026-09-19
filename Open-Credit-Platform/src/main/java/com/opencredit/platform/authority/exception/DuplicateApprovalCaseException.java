package com.opencredit.platform.authority.exception;

import java.util.UUID;

public class DuplicateApprovalCaseException extends RuntimeException {

    public DuplicateApprovalCaseException(UUID decisionId) {
        super("An approval case has already been opened for credit decision " + decisionId);
    }
}
