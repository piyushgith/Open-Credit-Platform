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
 * One fact computed for a {@link FinancialAnalysisRun}. A fact whose required inputs were
 * missing at run time has no row here at all — absence, not zero or null, is how "not
 * computable" is represented.
 */
@Entity
@Table(name = "derived_financial_fact")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DerivedFinancialFact {

    @Id
    private UUID id;

    @Column(name = "analysis_run_id", nullable = false)
    private UUID analysisRunId;

    @Enumerated(EnumType.STRING)
    @Column(name = "fact_code", nullable = false)
    private DerivedFinancialFactCode factCode;

    @Column(name = "value", nullable = false)
    private BigDecimal value;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
