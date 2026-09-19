package com.opencredit.platform.authority.model;

import com.opencredit.platform.loan.ProductType;
import com.opencredit.platform.scoring.model.RiskGrade;
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
 * One configurable row of the authority matrix: {@code productType}, {@code riskGrade},
 * {@code minAmount}/{@code maxAmount} are all optional match criteria (null = "any"); the first
 * active entry (ordered by {@code matchOrder}) whose criteria all match wins. See
 * {@link com.opencredit.platform.authority.support.AuthorityMatrixResolver}.
 */
@Entity
@Table(name = "authority_matrix_entry")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorityMatrixEntry {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type")
    private ProductType productType;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_grade")
    private RiskGrade riskGrade;

    @Column(name = "min_amount")
    private BigDecimal minAmount;

    @Column(name = "max_amount")
    private BigDecimal maxAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_level", nullable = false)
    private ApprovalLevel requiredLevel;

    @Column(name = "match_order", nullable = false)
    private int matchOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
