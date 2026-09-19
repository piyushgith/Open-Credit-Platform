package com.opencredit.platform.disbursement.model;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One disbursement case per {@code sanction} ({@code sanction_id UNIQUE}), lazily created on the
 * first {@link DisbursementTranche}, mirroring how {@code UnderwritingService.startAttempt} lazily
 * creates the {@code UnderwritingCase}. {@code disbursedTotal} is the running total across every
 * tranche; {@code @Version} protects it from a lost update under concurrent tranche requests.
 */
@Entity
@Table(name = "disbursement")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Disbursement {

    @Id
    private UUID id;

    @Column(name = "sanction_id", nullable = false, unique = true)
    private UUID sanctionId;

    @Column(name = "disbursed_total", nullable = false)
    private BigDecimal disbursedTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DisbursementStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
