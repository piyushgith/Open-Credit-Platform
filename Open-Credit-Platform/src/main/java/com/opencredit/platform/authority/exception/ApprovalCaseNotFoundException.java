package com.opencredit.platform.authority.exception;

import java.util.UUID;

public class ApprovalCaseNotFoundException extends RuntimeException {

    private ApprovalCaseNotFoundException(String message) {
        super(message);
    }

    public static ApprovalCaseNotFoundException byId(UUID caseId) {
        return new ApprovalCaseNotFoundException("No approval case found with id: " + caseId);
    }

    public static ApprovalCaseNotFoundException byDecisionId(UUID decisionId) {
        return new ApprovalCaseNotFoundException("No approval case has been opened for credit decision: " + decisionId);
    }
}
