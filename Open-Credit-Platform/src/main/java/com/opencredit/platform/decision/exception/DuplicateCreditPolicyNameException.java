package com.opencredit.platform.decision.exception;

public class DuplicateCreditPolicyNameException extends RuntimeException {

    public DuplicateCreditPolicyNameException(String name) {
        super("A credit policy already exists with name: " + name);
    }
}
