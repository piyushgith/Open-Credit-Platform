package com.opencredit.platform.scoring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScorecardResponse {

    private UUID id;
    private String name;
    private boolean active;
    private int baseScore;
    private int minScore;
    private int maxScore;
    private Instant createdAt;
    private List<ScorecardRuleResponse> rules;
}
