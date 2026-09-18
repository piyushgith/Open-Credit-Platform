package com.opencredit.platform.underwriting.exception;

import com.opencredit.platform.loan.model.ApplicationStatus;

public class InvalidUnderwritingStateException extends RuntimeException {

    public InvalidUnderwritingStateException(ApplicationStatus applicationStatus) {
        super("Underwriting cannot start while the application is in state: " + applicationStatus);
    }
}
