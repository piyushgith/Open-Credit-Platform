package com.opencredit.platform.authority.dto;

import com.opencredit.platform.authority.model.ApprovalLevel;
import com.opencredit.platform.loan.ProductType;
import com.opencredit.platform.scoring.model.RiskGrade;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AuthorityMatrixEntryRequest {

    private ProductType productType;

    private RiskGrade riskGrade;

    @Digits(integer = 17, fraction = 2)
    private BigDecimal minAmount;

    @Digits(integer = 17, fraction = 2)
    private BigDecimal maxAmount;

    @NotNull
    private ApprovalLevel requiredLevel;

    @NotNull
    private Integer matchOrder;

    @NotNull
    private Boolean active;
}
