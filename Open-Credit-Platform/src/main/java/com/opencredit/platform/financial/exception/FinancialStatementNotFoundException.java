package com.opencredit.platform.financial.exception;

import java.util.UUID;

public class FinancialStatementNotFoundException extends RuntimeException {

    public FinancialStatementNotFoundException(UUID statementId) {
        super("No financial statement found with id: " + statementId);
    }
}
