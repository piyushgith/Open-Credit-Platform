package com.opencredit.platform.authority.exception;

public class SelfApprovalException extends RuntimeException {

    public SelfApprovalException(String username) {
        super("Maker '" + username + "' cannot also act as checker on the same approval case");
    }
}
