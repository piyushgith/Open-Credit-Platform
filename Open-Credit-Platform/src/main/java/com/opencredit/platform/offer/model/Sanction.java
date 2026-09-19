package com.opencredit.platform.offer.model;

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
 * One sanction per {@code loan_application} ({@code application_id UNIQUE}, like
 * {@code UnderwritingCase}), snapshotting the terms of the {@link Offer} selected at sanction time.
 * No separate status: existence of this row is the state — {@code my_docs/plan.md}'s domain model
 * lists no {@code SanctionStatus}.
 */
@Entity
@Table(name = "sanction")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Sanction {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false, unique = true)
    private UUID applicationId;

    @Column(name = "offer_id", nullable = false)
    private UUID offerId;

    @Column(name = "sanctioned_amount", nullable = false)
    private BigDecimal sanctionedAmount;

    @Column(name = "interest_rate", nullable = false)
    private BigDecimal interestRate;

    @Column(name = "tenure_months", nullable = false)
    private Integer tenureMonths;

    @Column(name = "monthly_emi", nullable = false)
    private BigDecimal monthlyEmi;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
