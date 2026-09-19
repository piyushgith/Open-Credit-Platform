package com.opencredit.platform.decision.model;

import com.opencredit.platform.scoring.model.RiskGrade;

/**
 * A fixed, documented placeholder for "who must sign off on this decision" — <strong>not</strong>
 * the real {@code AuthorityMatrix} (loan amount / branch / product aware), which is Week 8's job
 * per the roadmap. Same "fixed enum, not a configurable table" precedent as {@link RiskGrade}'s
 * score cut-offs.
 *
 * <p>Mapping: {@code DECLINE} always resolves to {@code AUTO} (no approval authority is needed to
 * decline). {@code APPROVE} resolves to {@code AUTO} for a strong risk grade ({@code A}/{@code B})
 * and {@code CREDIT_OFFICER} otherwise, since the score cleared the policy but the underlying risk
 * grade is weaker. {@code REFER} always resolves to {@code SENIOR_CREDIT_MANAGER}, since a refer
 * means at least one soft rule failed and needs manual review.
 */
public enum RequiredAuthority {
    AUTO,
    CREDIT_OFFICER,
    SENIOR_CREDIT_MANAGER;

    public static RequiredAuthority forOutcome(DecisionOutcome outcome, RiskGrade riskGrade) {
        return switch (outcome) {
            case DECLINE -> AUTO;
            case REFER -> SENIOR_CREDIT_MANAGER;
            case APPROVE -> (riskGrade == RiskGrade.A || riskGrade == RiskGrade.B) ? AUTO : CREDIT_OFFICER;
        };
    }
}
