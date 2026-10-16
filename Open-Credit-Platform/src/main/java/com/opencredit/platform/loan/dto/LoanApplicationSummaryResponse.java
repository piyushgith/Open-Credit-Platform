package com.opencredit.platform.loan.dto;

import com.opencredit.platform.loan.ProductType;
import com.opencredit.platform.loan.model.ApplicationStatus;
import com.opencredit.platform.loan.model.DecisionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of the application list: the typed columns only, no product-specific details. */
@Data
@Builder
public class LoanApplicationSummaryResponse {

    private String applicationReference;
    private String applicantName;
    private ProductType productType;
    private BigDecimal requestedAmount;
    private Integer tenureMonths;
    private ApplicationStatus status;
    private DecisionStatus decision;
    private Instant createdAt;
}
