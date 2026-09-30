package com.opencredit.platform.scoring.dto;

import com.opencredit.platform.scoring.model.ScoreFactorCode;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ScorecardRuleRequest {

    @NotNull
    private ScoreFactorCode factorCode;

    @NotNull
    private Integer bandOrder;

    @Digits(integer = 15, fraction = 4)
    private BigDecimal minValue;

    @Digits(integer = 15, fraction = 4)
    private BigDecimal maxValue;

    @NotNull
    private Integer points;
}
