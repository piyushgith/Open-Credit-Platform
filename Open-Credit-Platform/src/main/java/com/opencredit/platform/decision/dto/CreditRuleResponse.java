package com.opencredit.platform.decision.dto;

import com.opencredit.platform.decision.model.ComparisonOperator;
import com.opencredit.platform.decision.model.CreditRuleFactorCode;
import com.opencredit.platform.decision.model.RuleSeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditRuleResponse {

    private UUID id;
    private CreditRuleFactorCode factorCode;
    private ComparisonOperator operator;
    private BigDecimal thresholdValue;
    private RuleSeverity severity;
    private int ruleOrder;
    private String description;
}
