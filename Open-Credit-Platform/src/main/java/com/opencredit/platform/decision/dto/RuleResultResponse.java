package com.opencredit.platform.decision.dto;

import com.opencredit.platform.decision.model.ComparisonOperator;
import com.opencredit.platform.decision.model.CreditRuleFactorCode;
import com.opencredit.platform.decision.model.RuleSeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleResultResponse {

    private CreditRuleFactorCode factorCode;
    private ComparisonOperator operator;
    private BigDecimal thresholdValue;
    private BigDecimal actualValue;
    private RuleSeverity severity;
    private String description;
}
