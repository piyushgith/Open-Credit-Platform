package com.opencredit.platform.scoring.dto;

import com.opencredit.platform.scoring.model.ScoreFactorCode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ScorecardRuleRequest {

    @NotNull
    private ScoreFactorCode factorCode;

    @NotNull
    private Integer bandOrder;

    private BigDecimal minValue;

    private BigDecimal maxValue;

    @NotNull
    private Integer points;
}
