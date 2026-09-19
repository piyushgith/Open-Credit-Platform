package com.opencredit.platform.authority.dto;

import com.opencredit.platform.authority.model.ApprovalCaseStatus;
import com.opencredit.platform.authority.model.ApprovalLevel;
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
public class ApprovalCaseResponse {

    private UUID id;
    private UUID decisionId;
    private ApprovalLevel requiredLevel;
    private ApprovalCaseStatus status;
    private List<ApprovalDecisionResponse> decisions;
    private Instant createdAt;
}
