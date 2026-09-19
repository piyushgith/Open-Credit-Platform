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
 * One evaluated factor contributing to a {@link Score}: the resolved input {@code value}, the
 * matched band's {@code points}, and (when points are non-zero) a human-readable
 * {@code description} from {@link ScoreFactorCode#riskLabel()}/{@link ScoreFactorCode#strengthLabel()}.
 * A row exists for every factor whose input was resolvable — not just the ones that moved the
 * score — same audit-everything-evaluable rule as {@code RiskIndicator}. {@code description} is
 * {@code null} for a factor that landed in a neutral (zero-point) band.
 */
@Entity
@Table(name = "risk_factor")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskFactor {

    @Id
    private UUID id;

    @Column(name = "score_id", nullable = false)
    private UUID scoreId;

    @Enumerated(EnumType.STRING)
    @Column(name = "factor_code", nullable = false)
    private ScoreFactorCode factorCode;

    @Column(name = "value", nullable = false)
    private BigDecimal value;

    @Column(name = "points", nullable = false)
    private int points;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
