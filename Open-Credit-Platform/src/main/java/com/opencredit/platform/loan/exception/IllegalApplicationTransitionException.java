package com.opencredit.platform.loan.exception;

import com.opencredit.platform.loan.model.ApplicationStatus;

public class IllegalApplicationTransitionException extends RuntimeException {

    public IllegalApplicationTransitionException(ApplicationStatus from, ApplicationStatus to) {
        super("Cannot transition loan application from " + from + " to " + to);
    }
}
