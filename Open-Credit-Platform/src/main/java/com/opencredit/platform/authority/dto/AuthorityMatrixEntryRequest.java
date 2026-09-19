package com.opencredit.platform.authority.dto;

import com.opencredit.platform.authority.model.ApprovalLevel;
import com.opencredit.platform.loan.ProductType;
import com.opencredit.platform.scoring.model.RiskGrade;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AuthorityMatrixEntryRequest {

    private ProductType productType;

    private RiskGrade riskGrade;

    private BigDecimal minAmount;

    private BigDecimal maxAmount;

    @NotNull
    private ApprovalLevel requiredLevel;

    @NotNull
    private Integer matchOrder;

    @NotNull
    private Boolean active;
}
