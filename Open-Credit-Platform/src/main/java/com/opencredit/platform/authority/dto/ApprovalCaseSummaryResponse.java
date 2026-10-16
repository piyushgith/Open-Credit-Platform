package com.opencredit.platform.authority.dto;

import com.opencredit.platform.authority.model.ApprovalCaseStatus;
import com.opencredit.platform.authority.model.ApprovalLevel;
import com.opencredit.platform.loan.ProductType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row of the approval queue, with the application it belongs to. */
public record ApprovalCaseSummaryResponse(
        UUID id,
        UUID decisionId,
        String applicationReference,
        String applicantName,
        ProductType productType,
        BigDecimal requestedAmount,
        ApprovalLevel requiredLevel,
        ApprovalCaseStatus status,
        Instant createdAt
) {
}
