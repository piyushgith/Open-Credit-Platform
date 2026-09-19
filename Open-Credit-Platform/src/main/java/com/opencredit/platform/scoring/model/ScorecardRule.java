package com.opencredit.platform.scoring.model;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One numeric band for a {@link ScoreFactorCode} on a {@link Scorecard}: half-open
 * {@code [minValue, maxValue)}, either bound {@code null} meaning unbounded on that side. A
 * factor's input value falling in this band contributes {@code points} to the total score —
 * this is the entire "rule engine": range comparison, no expression language, no reflection.
 */
@Entity
@Table(name = "scorecard_rule")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScorecardRule {

    @Id
    private UUID id;

    @Column(name = "scorecard_id", nullable = false)
    private UUID scorecardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "factor_code", nullable = false)
    private ScoreFactorCode factorCode;

    @Column(name = "band_order", nullable = false)
    private int bandOrder;

    @Column(name = "min_value")
    private BigDecimal minValue;

    @Column(name = "max_value")
    private BigDecimal maxValue;

    @Column(name = "points", nullable = false)
    private int points;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
