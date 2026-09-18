package com.opencredit.platform.underwriting.exception;

import com.opencredit.platform.loan.model.ApplicationStatus;
import com.opencredit.platform.loan.model.DecisionStatus;

public class UnderwritingNotRetryableException extends RuntimeException {

    public UnderwritingNotRetryableException(String referenceNumber, ApplicationStatus status, DecisionStatus decision) {
        super("Loan application " + referenceNumber + " is not retryable: status=" + status + ", decision=" + decision
                + " (only an application parked at UNDERWRITING with a REFERRED decision can be retried)");
    }
}
