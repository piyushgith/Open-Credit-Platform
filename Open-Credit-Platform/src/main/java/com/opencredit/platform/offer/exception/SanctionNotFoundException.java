package com.opencredit.platform.offer.exception;

import java.util.UUID;

public class SanctionNotFoundException extends RuntimeException {

    public SanctionNotFoundException(String referenceNumber) {
        super("No sanction found for application: " + referenceNumber);
    }

    public SanctionNotFoundException(UUID applicationId) {
        super("No sanction found for application: " + applicationId);
    }
}
