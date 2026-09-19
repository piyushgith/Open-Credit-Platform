package com.opencredit.platform.disbursement.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * One independently traceable disbursement request against a {@link Disbursement}: sequential
 * {@code trancheNumber}, {@code amount}, and a client-supplied {@code requestReference} used as an
 * idempotency key. {@code UNIQUE (disbursement_id, request_reference)} at the database level is the
 * real guard against a duplicate request, the same "DB constraint is the lock" pattern as
 * {@code approval_decision}'s {@code UNIQUE (approval_case_id, role)}.
 */
@Entity
@Table(name = "disbursement_tranche")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisbursementTranche {

    @Id
    private UUID id;

    @Column(name = "disbursement_id", nullable = false)
    private UUID disbursementId;

    @Column(name = "tranche_number", nullable = false)
    private Integer trancheNumber;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "request_reference", nullable = false)
    private String requestReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
