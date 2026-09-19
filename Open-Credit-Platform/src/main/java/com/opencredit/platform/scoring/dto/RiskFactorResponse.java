package com.opencredit.platform.scoring.dto;

import com.opencredit.platform.scoring.model.ScoreFactorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskFactorResponse {

    private ScoreFactorCode factorCode;
    private BigDecimal value;
    private int points;
    private String description;
}
