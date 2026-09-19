package com.opencredit.platform.decision.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A named, versionable set of {@link CreditRule}s. At most one policy is {@code active} at a
 * time (enforced by the partial unique index {@code uq_credit_policy_active}, same mechanism as
 * {@code scorecard.active}) — that is the one {@code DecisionService} decides against.
 */
@Entity
@Table(name = "credit_policy")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditPolicy {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
