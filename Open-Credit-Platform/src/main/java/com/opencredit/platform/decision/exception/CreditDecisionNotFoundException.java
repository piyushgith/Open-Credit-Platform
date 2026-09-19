package com.opencredit.platform.decision.exception;

import java.util.UUID;

public class CreditDecisionNotFoundException extends RuntimeException {

    public CreditDecisionNotFoundException(UUID decisionId) {
        super("No credit decision found with id: " + decisionId);
    }
}
