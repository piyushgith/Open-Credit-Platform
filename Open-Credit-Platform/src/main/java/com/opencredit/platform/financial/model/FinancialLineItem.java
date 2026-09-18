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
 * One raw, source-supplied figure on a {@link FinancialStatement}. Preserved exactly as
 * submitted — never overwritten by a derived/calculated value.
 */
@Entity
@Table(name = "financial_line_item")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialLineItem {

    @Id
    private UUID id;

    @Column(name = "statement_id", nullable = false)
    private UUID statementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_item_code", nullable = false)
    private FinancialLineItemCode lineItemCode;

    @Column(name = "value", nullable = false)
    private BigDecimal value;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
