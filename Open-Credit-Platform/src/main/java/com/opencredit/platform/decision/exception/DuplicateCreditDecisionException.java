package com.opencredit.platform.decision.exception;

import java.util.UUID;

public class DuplicateCreditDecisionException extends RuntimeException {

    public DuplicateCreditDecisionException(UUID scoreId, UUID policyId) {
        super("Score " + scoreId + " has already been decided against credit policy " + policyId);
    }
}
