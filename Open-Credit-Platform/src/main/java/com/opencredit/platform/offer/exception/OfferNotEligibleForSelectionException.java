package com.opencredit.platform.offer.exception;

import java.util.UUID;

public class OfferNotEligibleForSelectionException extends RuntimeException {

    public OfferNotEligibleForSelectionException(UUID offerId, String reason) {
        super("Offer " + offerId + " cannot be selected: " + reason);
    }
}
