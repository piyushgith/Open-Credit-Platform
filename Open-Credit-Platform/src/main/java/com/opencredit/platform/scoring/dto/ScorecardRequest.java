package com.opencredit.platform.scoring.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ScorecardRequest {

    @NotBlank
    private String name;

    @NotNull
    private Integer baseScore;

    @NotNull
    private Integer minScore;

    @NotNull
    private Integer maxScore;
}
