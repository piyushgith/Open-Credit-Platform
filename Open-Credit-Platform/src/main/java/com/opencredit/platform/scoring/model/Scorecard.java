package com.opencredit.platform.scoring.model;

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
 * A named, versionable scoring configuration: its {@link ScorecardRule} bands plus the score
 * envelope ({@code baseScore} clamped to {@code [minScore, maxScore]}) are all data, not code.
 * At most one scorecard is {@code active} at a time (enforced by the partial unique index
 * {@code uq_scorecard_active}, same mechanism as {@code underwriting_attempt.active}) — that is
 * the one {@code ScoreService} scores against.
 */
@Entity
@Table(name = "scorecard")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Scorecard {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "base_score", nullable = false)
    private int baseScore;

    @Column(name = "min_score", nullable = false)
    private int minScore;

    @Column(name = "max_score", nullable = false)
    private int maxScore;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
