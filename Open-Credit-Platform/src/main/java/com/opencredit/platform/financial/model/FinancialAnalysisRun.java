package com.opencredit.platform.financial.model;

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
 * One execution of {@link com.opencredit.platform.financial.support.DerivedFactCalculator}
 * against a {@link FinancialStatement}'s line items. Re-analyzing a statement creates a new run
 * rather than mutating the previous one, so every past calculation stays visible.
 */
@Entity
@Table(name = "financial_analysis_run")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialAnalysisRun {

    @Id
    private UUID id;

    @Column(name = "statement_id", nullable = false)
    private UUID statementId;

    @Column(name = "run_at", nullable = false)
    private Instant runAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
