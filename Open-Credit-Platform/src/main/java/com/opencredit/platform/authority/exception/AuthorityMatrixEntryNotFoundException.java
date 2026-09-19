package com.opencredit.platform.authority.exception;

import java.util.UUID;

public class AuthorityMatrixEntryNotFoundException extends RuntimeException {

    public AuthorityMatrixEntryNotFoundException(UUID entryId) {
        super("No authority matrix entry found with id: " + entryId);
    }
}
