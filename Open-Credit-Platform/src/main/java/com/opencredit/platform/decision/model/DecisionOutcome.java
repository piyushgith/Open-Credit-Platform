package com.opencredit.platform.decision.model;

/**
 * The outcome of a {@link CreditDecision}. Distinct from {@code loan.model.DecisionStatus}
 * (which drives the separate, FOIR-based decision {@code LoanProcessingStrategy} makes at
 * application submission time): this is the later-stage, policy-driven decision computed from a
 * {@code Score} once financial analysis has run. It does not write back to {@code LoanApplication}
 * directly here in {@code DecisionService} — {@code ApprovalService.openCase} does, once it has
 * resolved whether this outcome needs a human approval case at all (see its Javadoc).
 */
public enum DecisionOutcome {
    APPROVE,
    REFER,
    DECLINE
}
