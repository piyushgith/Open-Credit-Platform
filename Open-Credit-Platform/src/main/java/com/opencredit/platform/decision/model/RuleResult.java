package com.opencredit.platform.decision.model;

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
 * One evaluated {@link CreditRule} contributing to a {@link CreditDecision}: the resolved input
 * {@code actualValue}, the configured {@code thresholdValue}/{@code operator} it was compared
 * against, whether it {@code passed}, and a human-readable {@code description} — this is
 * "DecisionReason": rather than a separate persisted concept, the reason for the decision is
 * simply the description of each rule that failed (or, for context, passed). A row exists for
 * every rule whose input was resolvable — not just the failed ones — same audit-everything
 * precedent as {@code RiskFactor}/{@code RiskIndicator}.
 */
@Entity
@Table(name = "rule_result")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleResult {

    @Id
    private UUID id;

    @Column(name = "decision_id", nullable = false)
    private UUID decisionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "factor_code", nullable = false)
    private CreditRuleFactorCode factorCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false)
    private ComparisonOperator operator;

    @Column(name = "threshold_value", nullable = false)
    private BigDecimal thresholdValue;

    @Column(name = "actual_value", nullable = false)
    private BigDecimal actualValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private RuleSeverity severity;

    @Column(name = "passed", nullable = false)
    private boolean passed;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
