package com.opencredit.platform.underwriting.exception;

import com.opencredit.platform.underwriting.model.UnderwritingAttemptStatus;

public class IllegalUnderwritingAttemptTransitionException extends RuntimeException {

    public IllegalUnderwritingAttemptTransitionException(UnderwritingAttemptStatus from, UnderwritingAttemptStatus to) {
        super("Cannot transition underwriting attempt from " + from + " to " + to);
    }
}
