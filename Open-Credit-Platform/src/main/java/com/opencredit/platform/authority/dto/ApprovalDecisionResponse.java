package com.opencredit.platform.authority.dto;

import com.opencredit.platform.authority.model.ApprovalLevel;
import com.opencredit.platform.authority.model.ApprovalOutcome;
import com.opencredit.platform.authority.model.ApprovalRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalDecisionResponse {

    private ApprovalRole role;
    private String actorUsername;
    private ApprovalLevel actorLevel;
    private ApprovalOutcome outcome;
    private String comment;
    private Instant decidedAt;
}
