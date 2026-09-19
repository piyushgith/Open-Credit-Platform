package com.opencredit.platform.authority.dto;

import com.opencredit.platform.authority.model.ApprovalOutcome;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * {@code actorUsername}/{@code actorLevel} used to be asserted here by the caller, since no
 * user/security directory existed (Week 10's own TODO). Now that {@code AppUser} exists, both are
 * read from the authenticated principal ({@code SecurityContextHolder}) in
 * {@code ApprovalService.recordDecision} instead — a client can no longer claim to be anyone.
 */
@Data
public class ApprovalActionRequest {

    @NotNull
    private ApprovalOutcome outcome;

    private String comment;
}
