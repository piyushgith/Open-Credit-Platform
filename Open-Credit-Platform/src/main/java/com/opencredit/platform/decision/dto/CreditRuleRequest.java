package com.opencredit.platform.decision.dto;

import com.opencredit.platform.decision.model.ComparisonOperator;
import com.opencredit.platform.decision.model.CreditRuleFactorCode;
import com.opencredit.platform.decision.model.RuleSeverity;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreditRuleRequest {

    @NotNull
    private CreditRuleFactorCode factorCode;

    @NotNull
    private ComparisonOperator operator;

    @NotNull
    @Digits(integer = 15, fraction = 4)
    private BigDecimal thresholdValue;

    @NotNull
    private RuleSeverity severity;

    @NotNull
    private Integer ruleOrder;

    @Size(max = 200)
    private String description;
}
