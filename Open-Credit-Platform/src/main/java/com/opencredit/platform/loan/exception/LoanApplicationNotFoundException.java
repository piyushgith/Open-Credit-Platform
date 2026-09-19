package com.opencredit.platform.loan.exception;

import java.util.UUID;

public class LoanApplicationNotFoundException extends RuntimeException {

    public LoanApplicationNotFoundException(String reference) {
        super("No loan application found with reference: " + reference);
    }

    public LoanApplicationNotFoundException(UUID applicationId) {
        super("No loan application found with id: " + applicationId);
    }
}
