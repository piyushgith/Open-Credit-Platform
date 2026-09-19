package com.opencredit.platform.decision.dto;

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
public class CreditPolicyResponse {

    private UUID id;
    private String name;
    private boolean active;
    private Instant createdAt;
    private List<CreditRuleResponse> rules;
}
