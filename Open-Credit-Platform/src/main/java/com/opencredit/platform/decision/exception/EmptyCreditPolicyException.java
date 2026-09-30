package com.opencredit.platform.decision.exception;

import java.util.UUID;

public class EmptyCreditPolicyException extends RuntimeException {

    public EmptyCreditPolicyException(UUID policyId) {
        super("Credit policy " + policyId + " cannot be activated: it has no rules");
    }
}
