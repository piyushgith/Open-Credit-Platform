package com.opencredit.platform.decision.exception;

import java.util.UUID;

public class CreditPolicyNotFoundException extends RuntimeException {

    public CreditPolicyNotFoundException(UUID policyId) {
        super("No credit policy found with id: " + policyId);
    }
}
