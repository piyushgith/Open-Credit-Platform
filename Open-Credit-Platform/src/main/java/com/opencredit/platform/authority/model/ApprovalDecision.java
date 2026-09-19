package com.opencredit.platform.authority.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One recorded maker or checker action on an {@link ApprovalCase}. Append-only — never updated or
 * deleted, so history survives even a {@code REJECT}. {@code UNIQUE (approval_case_id, role)} at
 * the database level is the real guard against the same role acting twice, including under
 * concurrent requests; {@link com.opencredit.platform.authority.support.ApprovalLifecycle} is the
 * fast-path, non-authoritative check.
 */
@Entity
@Table(name = "approval_decision")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalDecision {

    @Id
    private UUID id;

    @Column(name = "approval_case_id", nullable = false)
    private UUID approvalCaseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private ApprovalRole role;

    @Column(name = "actor_username", nullable = false)
    private String actorUsername;

    /** Null for a {@code MAKER} row — only a {@code CHECKER}'s level is ever meaningful. */
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_level")
    private ApprovalLevel actorLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    private ApprovalOutcome outcome;

    @Column(name = "comment")
    private String comment;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;
}
