package com.opencredit.platform.decision.exception;

public class NoActiveCreditPolicyException extends RuntimeException {

    public NoActiveCreditPolicyException() {
        super("No active credit policy is configured");
    }
}
