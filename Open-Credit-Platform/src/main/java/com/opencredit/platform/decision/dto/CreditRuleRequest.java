package com.opencredit.platform.decision.dto;

import com.opencredit.platform.decision.model.ComparisonOperator;
import com.opencredit.platform.decision.model.CreditRuleFactorCode;
import com.opencredit.platform.decision.model.RuleSeverity;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreditRuleRequest {

    @NotNull
    private CreditRuleFactorCode factorCode;

    @NotNull
    private ComparisonOperator operator;

    @NotNull
    private BigDecimal thresholdValue;

    @NotNull
    private RuleSeverity severity;

    @NotNull
    private Integer ruleOrder;

    private String description;
}
