package com.opencredit.platform.authority.dto;

import com.opencredit.platform.authority.model.ApprovalLevel;
import com.opencredit.platform.authority.model.ApprovalOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * {@code actorLevel} is asserted by the caller rather than looked up, since no user/security
 * directory exists yet (Week 10). The server only enforces the maker-checker rules given whatever
 * identity and level are supplied.
 */
@Data
public class ApprovalActionRequest {

    @NotBlank
    private String actorUsername;

    @NotNull
    private ApprovalLevel actorLevel;

    @NotNull
    private ApprovalOutcome outcome;

    private String comment;
}
