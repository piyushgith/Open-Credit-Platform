package com.opencredit.platform.financial.exception;

import java.util.UUID;

public class FinancialAnalysisRunNotFoundException extends RuntimeException {

    public FinancialAnalysisRunNotFoundException(UUID analysisRunId) {
        super("No financial analysis run found with id: " + analysisRunId);
    }
}
