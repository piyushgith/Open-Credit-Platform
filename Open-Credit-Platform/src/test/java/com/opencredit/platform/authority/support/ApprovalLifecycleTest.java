package com.opencredit.platform.authority.support;

import com.opencredit.platform.authority.exception.IllegalApprovalTransitionException;
import com.opencredit.platform.authority.model.ApprovalCaseStatus;
import com.opencredit.platform.authority.model.ApprovalRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalLifecycleTest {

    @Test
    void makerCanActOnlyWhenPendingMaker() {
        assertThat(catchThrown(ApprovalCaseStatus.PENDING_MAKER, ApprovalRole.MAKER)).isNull();
    }

    @Test
    void checkerCanActOnlyWhenPendingChecker() {
        assertThat(catchThrown(ApprovalCaseStatus.PENDING_CHECKER, ApprovalRole.CHECKER)).isNull();
    }

    @Test
    void makerCannotActWhenPendingChecker() {
        assertThatThrownBy(() -> ApprovalLifecycle.assertCanAct(ApprovalCaseStatus.PENDING_CHECKER, ApprovalRole.MAKER))
                .isInstanceOf(IllegalApprovalTransitionException.class);
    }

    @Test
    void checkerCannotActWhenPendingMaker() {
        assertThatThrownBy(() -> ApprovalLifecycle.assertCanAct(ApprovalCaseStatus.PENDING_MAKER, ApprovalRole.CHECKER))
                .isInstanceOf(IllegalApprovalTransitionException.class);
    }

    @Test
    void neitherRoleCanActOnceApproved() {
        assertThatThrownBy(() -> ApprovalLifecycle.assertCanAct(ApprovalCaseStatus.APPROVED, ApprovalRole.MAKER))
                .isInstanceOf(IllegalApprovalTransitionException.class);
        assertThatThrownBy(() -> ApprovalLifecycle.assertCanAct(ApprovalCaseStatus.APPROVED, ApprovalRole.CHECKER))
                .isInstanceOf(IllegalApprovalTransitionException.class);
    }

    @Test
    void neitherRoleCanActOnceRejected() {
        assertThatThrownBy(() -> ApprovalLifecycle.assertCanAct(ApprovalCaseStatus.REJECTED, ApprovalRole.MAKER))
                .isInstanceOf(IllegalApprovalTransitionException.class);
        assertThatThrownBy(() -> ApprovalLifecycle.assertCanAct(ApprovalCaseStatus.REJECTED, ApprovalRole.CHECKER))
                .isInstanceOf(IllegalApprovalTransitionException.class);
    }

    private static Exception catchThrown(ApprovalCaseStatus status, ApprovalRole role) {
        try {
            ApprovalLifecycle.assertCanAct(status, role);
            return null;
        } catch (Exception ex) {
            return ex;
        }
    }
}
