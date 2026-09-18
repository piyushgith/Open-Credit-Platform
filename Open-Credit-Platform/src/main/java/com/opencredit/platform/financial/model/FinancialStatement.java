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
 * A financial statement submitted for a {@code loan_application}, for one {@link FinancialPeriod}.
 * Holds no figures itself — those live in {@link FinancialLineItem} rows.
 */
@Entity
@Table(name = "financial_statement")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialStatement {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "period_id", nullable = false, unique = true)
    private UUID periodId;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
