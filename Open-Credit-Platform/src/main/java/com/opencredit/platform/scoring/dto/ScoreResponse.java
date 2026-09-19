package com.opencredit.platform.scoring.dto;

import com.opencredit.platform.scoring.model.RiskGrade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code contributingFactors} holds every evaluated factor whose matched band awarded positive
 * points, {@code failedFactors} every one with negative points; a factor that landed in a
 * neutral (zero-point) band appears in neither list.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreResponse {

    private UUID id;
    private UUID analysisRunId;
    private String scorecardName;
    private int totalScore;
    private RiskGrade riskGrade;
    private List<RiskFactorResponse> contributingFactors;
    private List<RiskFactorResponse> failedFactors;
    private Instant createdAt;
}
