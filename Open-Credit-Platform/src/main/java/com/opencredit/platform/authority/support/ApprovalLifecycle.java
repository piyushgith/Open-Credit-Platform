package com.opencredit.platform.authority.support;

import com.opencredit.platform.authority.exception.IllegalApprovalTransitionException;
import com.opencredit.platform.authority.model.ApprovalCaseStatus;
import com.opencredit.platform.authority.model.ApprovalRole;

/**
 * Guards the approval case state machine: {@code PENDING_MAKER -> PENDING_CHECKER} (a maker acts)
 * and {@code PENDING_CHECKER -> (APPROVED | REJECTED)} (a checker acts, outcome decides which).
 * {@code APPROVED}/{@code REJECTED} are terminal. Pure and stateless, mirroring
 * {@code underwriting.support.UnderwritingAttemptLifecycle}.
 */
public final class ApprovalLifecycle {

    private ApprovalLifecycle() {
    }

    public static void assertCanAct(ApprovalCaseStatus status, ApprovalRole role) {
        boolean allowed = (role == ApprovalRole.MAKER && status == ApprovalCaseStatus.PENDING_MAKER)
                || (role == ApprovalRole.CHECKER && status == ApprovalCaseStatus.PENDING_CHECKER);
        if (!allowed) {
            throw new IllegalApprovalTransitionException(status, role);
        }
    }
}
