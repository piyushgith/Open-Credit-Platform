package com.opencredit.platform.scoring.exception;

import java.util.UUID;

public class ScorecardRuleNotFoundException extends RuntimeException {

    public ScorecardRuleNotFoundException(UUID ruleId) {
        super("No scorecard rule found with id: " + ruleId);
    }
}
