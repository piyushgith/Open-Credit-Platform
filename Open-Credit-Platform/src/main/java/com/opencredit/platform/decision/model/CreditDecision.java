package com.opencredit.platform.decision.model;

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
 * One deterministic decision of a {@code Score} against a {@link CreditPolicy}. Unique on
 * {@code (scoreId, policyId)} — re-deciding the same score under the same policy is rejected
 * rather than silently overwritten; deciding under a different (e.g. newly activated) policy
 * produces a new row. Does not write back to {@code LoanApplication} — see the Week 7 plan's
 * "Out of scope" section.
 */
@Entity
@Table(name = "credit_decision")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditDecision {

    @Id
    private UUID id;

    @Column(name = "score_id", nullable = false)
    private UUID scoreId;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    private DecisionOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_authority", nullable = false)
    private RequiredAuthority requiredAuthority;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
