package com.opencredit.platform.loan.strategy;

import com.opencredit.platform.loan.ProductType;
import com.opencredit.platform.loan.dto.LoanRequest;
import com.opencredit.platform.loan.dto.LoanResponse;

/**
 * Product-specific decisioning logic. Implementations are pure: they compute and
 * return a decision, they never touch the repository. Persistence stays in
 * {@code LoanApplicationService}, keeping strategies unit-testable with no Spring
 * context and no database.
 */
public interface LoanProcessingStrategy {

    ProductType getProductType();

    LoanResponse processLoan(LoanRequest request);
}
