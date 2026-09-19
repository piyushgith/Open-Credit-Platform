package com.opencredit.platform.authority.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One maker-checker case per {@code CreditDecision} ({@code decision_id UNIQUE} — a decision can
 * never have more than one case, opened or historical). {@code requiredLevel} is resolved once,
 * at open time, from the {@link AuthorityMatrixEntry} table and never recomputed, so a later
 * matrix change cannot retroactively alter an in-flight or decided case.
 */
@Entity
@Table(name = "approval_case")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalCase {

    @Id
    private UUID id;

    @Column(name = "decision_id", nullable = false, unique = true)
    private UUID decisionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_level", nullable = false)
    private ApprovalLevel requiredLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ApprovalCaseStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
