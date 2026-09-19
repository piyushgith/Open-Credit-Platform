package com.opencredit.platform.authority.exception;

public class NoMatchingAuthorityMatrixEntryException extends RuntimeException {

    public NoMatchingAuthorityMatrixEntryException() {
        super("No authority matrix entry matches this decision; configure a catch-all entry with no criteria");
    }
}
