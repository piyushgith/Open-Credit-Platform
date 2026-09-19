package com.opencredit.platform.decision.model;

/**
 * How a failed {@link CreditRule} affects the overall {@link DecisionOutcome}: a failed
 * {@code HARD} rule forces {@code DECLINE}; a failed {@code SOFT} rule (with every {@code HARD}
 * rule passing) forces {@code REFER}.
 */
public enum RuleSeverity {
    HARD,
    SOFT
}
