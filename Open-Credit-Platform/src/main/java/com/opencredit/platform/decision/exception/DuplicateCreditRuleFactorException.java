package com.opencredit.platform.decision.exception;

import com.opencredit.platform.decision.model.CreditRuleFactorCode;

public class DuplicateCreditRuleFactorException extends RuntimeException {

    public DuplicateCreditRuleFactorException(CreditRuleFactorCode factorCode) {
        super("This policy already has a rule for factor: " + factorCode);
    }
}
