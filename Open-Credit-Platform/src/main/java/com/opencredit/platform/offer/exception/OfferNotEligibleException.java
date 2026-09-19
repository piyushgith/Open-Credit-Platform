package com.opencredit.platform.offer.exception;

import com.opencredit.platform.loan.model.ApplicationStatus;

public class OfferNotEligibleException extends RuntimeException {

    public OfferNotEligibleException(String referenceNumber, ApplicationStatus status) {
        super("Cannot create an offer for application " + referenceNumber
                + ": it must be OFFERED with an APPROVED underwriting decision, currently " + status);
    }
}
