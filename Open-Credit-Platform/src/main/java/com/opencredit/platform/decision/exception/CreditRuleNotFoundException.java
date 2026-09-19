package com.opencredit.platform.decision.exception;

import java.util.UUID;

public class CreditRuleNotFoundException extends RuntimeException {

    public CreditRuleNotFoundException(UUID ruleId) {
        super("No credit rule found with id: " + ruleId);
    }
}
