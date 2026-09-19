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
 * One threshold condition on a {@link CreditPolicy}: {@code factorCode operator thresholdValue},
 * e.g. {@code DSCR >= 1.5}. This is the entire "rule engine" — one comparison per rule, no
 * AND/OR trees, no expression language; a policy's rule set is implicitly ANDed by
 * {@code DecisionEngine}. A failed {@code HARD} rule forces {@code DECLINE}; a failed
 * {@code SOFT} rule forces {@code REFER}. At most one rule per {@code factorCode} per policy
 * (enforced in {@code CreditPolicyAdminService}, not the database).
 */
@Entity
@Table(name = "credit_rule")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditRule {

    @Id
    private UUID id;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "factor_code", nullable = false)
    private CreditRuleFactorCode factorCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false)
    private ComparisonOperator operator;

    @Column(name = "threshold_value", nullable = false)
    private BigDecimal thresholdValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private RuleSeverity severity;

    @Column(name = "rule_order", nullable = false)
    private int ruleOrder;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
