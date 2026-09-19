package com.opencredit.platform.decision.exception;

import java.util.UUID;

public class ConcurrentCreditPolicyActivationException extends RuntimeException {

    public ConcurrentCreditPolicyActivationException(UUID policyId) {
        super("Another credit policy was activated concurrently; retry activating policy " + policyId);
    }
}
