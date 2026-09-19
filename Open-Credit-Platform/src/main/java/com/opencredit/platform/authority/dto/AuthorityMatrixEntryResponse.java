package com.opencredit.platform.authority.dto;

import com.opencredit.platform.authority.model.ApprovalLevel;
import com.opencredit.platform.loan.ProductType;
import com.opencredit.platform.scoring.model.RiskGrade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorityMatrixEntryResponse {

    private UUID id;
    private ProductType productType;
    private RiskGrade riskGrade;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private ApprovalLevel requiredLevel;
    private int matchOrder;
    private boolean active;
    private Instant createdAt;
}
