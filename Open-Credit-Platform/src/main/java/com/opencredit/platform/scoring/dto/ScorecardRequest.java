package com.opencredit.platform.scoring.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ScorecardRequest {

    @NotBlank
    @Size(max = 80)
    private String name;

    @NotNull
    private Integer baseScore;

    @NotNull
    private Integer minScore;

    @NotNull
    private Integer maxScore;
}
