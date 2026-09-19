package com.opencredit.platform.offer.exception;

import java.util.UUID;

public class NoOfferSelectedException extends RuntimeException {

    public NoOfferSelectedException(UUID applicationId) {
        super("No offer has been selected for application: " + applicationId);
    }
}
