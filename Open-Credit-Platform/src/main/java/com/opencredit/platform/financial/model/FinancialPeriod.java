package com.opencredit.platform.financial.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The reporting period a {@link FinancialStatement} was submitted for. Owned 1:1 by a single
 * statement — created alongside it, never shared or reused across statements.
 */
@Entity
@Table(name = "financial_period")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialPeriod {

    @Id
    private UUID id;

    @Column(name = "period_label", nullable = false)
    private String periodLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false)
    private FinancialPeriodType periodType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
