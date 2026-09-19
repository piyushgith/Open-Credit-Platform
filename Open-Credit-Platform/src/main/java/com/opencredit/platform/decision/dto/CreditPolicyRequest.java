package com.opencredit.platform.decision.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreditPolicyRequest {

    @NotBlank
    private String name;
}
