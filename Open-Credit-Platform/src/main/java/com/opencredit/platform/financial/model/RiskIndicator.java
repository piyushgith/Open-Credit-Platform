package com.opencredit.platform.financial.model;

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
 * One risk check evaluated for a {@link FinancialAnalysisRun}. A row exists only when the check
 * was evaluable (its inputs were present) — {@code triggered} records the outcome either way, so
 * the run's audit trail shows every check that ran, not just the ones that fired.
 */
@Entity
@Table(name = "risk_indicator")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskIndicator {

    @Id
    private UUID id;

    @Column(name = "analysis_run_id", nullable = false)
    private UUID analysisRunId;

    @Enumerated(EnumType.STRING)
    @Column(name = "indicator_code", nullable = false)
    private RiskIndicatorCode indicatorCode;

    @Column(name = "triggered", nullable = false)
    private boolean triggered;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
