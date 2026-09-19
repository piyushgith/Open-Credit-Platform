package com.opencredit.platform.offer.exception;

import java.util.UUID;

public class DuplicateSanctionException extends RuntimeException {

    public DuplicateSanctionException(UUID applicationId) {
        super("Application " + applicationId + " has already been sanctioned");
    }
}
