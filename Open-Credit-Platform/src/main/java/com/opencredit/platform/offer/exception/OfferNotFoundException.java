package com.opencredit.platform.offer.exception;

import java.util.UUID;

public class OfferNotFoundException extends RuntimeException {

    public OfferNotFoundException(UUID offerId) {
        super("No offer found with id: " + offerId);
    }
}
