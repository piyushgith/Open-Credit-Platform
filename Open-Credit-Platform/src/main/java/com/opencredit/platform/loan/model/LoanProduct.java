package com.opencredit.platform.loan.model;

import com.opencredit.platform.loan.ProductType;
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
 * Product catalog entry: the amount/tenure bounds a {@link ProductType} accepts.
 * {@code loan_application} requests are validated against these at apply time.
 */
@Entity
@Table(name = "loan_product")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanProduct {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, unique = true)
    private ProductType productType;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "min_amount", nullable = false)
    private BigDecimal minAmount;

    @Column(name = "max_amount", nullable = false)
    private BigDecimal maxAmount;

    @Column(name = "min_tenure_months", nullable = false)
    private Integer minTenureMonths;

    @Column(name = "max_tenure_months", nullable = false)
    private Integer maxTenureMonths;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
