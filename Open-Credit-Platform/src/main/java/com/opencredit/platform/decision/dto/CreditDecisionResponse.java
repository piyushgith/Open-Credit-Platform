package com.opencredit.platform.decision.dto;

import com.opencredit.platform.decision.model.DecisionOutcome;
import com.opencredit.platform.decision.model.RequiredAuthority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code passedRules} holds every evaluated rule that passed, {@code failedRules} every one that
 * failed — together these are "DecisionReason": the explanation for {@code outcome} is simply
 * the descriptions on {@code failedRules} (or, for an approval, the absence of any).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditDecisionResponse {

    private UUID id;
    private UUID scoreId;
    private String policyName;
    private DecisionOutcome outcome;
    private RequiredAuthority requiredAuthority;
    private List<RuleResultResponse> passedRules;
    private List<RuleResultResponse> failedRules;
    private Instant createdAt;
}
