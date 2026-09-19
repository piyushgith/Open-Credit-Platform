package com.opencredit.platform.offer.exception;

public class OfferAlreadySelectedException extends RuntimeException {

    public OfferAlreadySelectedException(String referenceNumber) {
        super("An offer has already been selected for application " + referenceNumber);
    }
}
