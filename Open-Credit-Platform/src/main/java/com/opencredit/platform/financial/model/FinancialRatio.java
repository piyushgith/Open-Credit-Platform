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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One ratio computed for a {@link FinancialAnalysisRun}. A ratio whose inputs were missing or
 * whose denominator was zero has no row here at all — same absence-over-defaulting rule as
 * {@link DerivedFinancialFact}.
 */
@Entity
@Table(name = "financial_ratio")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialRatio {

    @Id
    private UUID id;

    @Column(name = "analysis_run_id", nullable = false)
    private UUID analysisRunId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ratio_code", nullable = false)
    private FinancialRatioCode ratioCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private FinancialCategory category;

    @Column(name = "value", nullable = false)
    private BigDecimal value;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
