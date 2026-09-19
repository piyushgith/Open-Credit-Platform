package com.opencredit.platform.scoring.dto;

import com.opencredit.platform.scoring.model.ScoreFactorCode;
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
public class ScorecardRuleResponse {

    private UUID id;
    private ScoreFactorCode factorCode;
    private int bandOrder;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private int points;
}
