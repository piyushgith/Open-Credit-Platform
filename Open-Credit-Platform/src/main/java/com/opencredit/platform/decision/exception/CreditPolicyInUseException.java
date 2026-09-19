package com.opencredit.platform.decision.exception;

import java.util.UUID;

public class CreditPolicyInUseException extends RuntimeException {

    public CreditPolicyInUseException(UUID policyId, String reason) {
        super("Credit policy " + policyId + " cannot be deleted: " + reason);
    }
}
