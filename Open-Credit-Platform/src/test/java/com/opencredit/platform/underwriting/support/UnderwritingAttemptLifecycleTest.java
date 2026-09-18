package com.opencredit.platform.underwriting.support;

import com.opencredit.platform.underwriting.exception.IllegalUnderwritingAttemptTransitionException;
import com.opencredit.platform.underwriting.model.UnderwritingAttemptStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnderwritingAttemptLifecycleTest {

    @Test
    void allowsInProgressToApprove() {
        assertThatCode(() -> UnderwritingAttemptLifecycle.assertTransition(
                UnderwritingAttemptStatus.IN_PROGRESS, UnderwritingAttemptStatus.APPROVED))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsInProgressToDecline() {
        assertThatCode(() -> UnderwritingAttemptLifecycle.assertTransition(
                UnderwritingAttemptStatus.IN_PROGRESS, UnderwritingAttemptStatus.DECLINED))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsInProgressToRefer() {
        assertThatCode(() -> UnderwritingAttemptLifecycle.assertTransition(
                UnderwritingAttemptStatus.IN_PROGRESS, UnderwritingAttemptStatus.REFERRED))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsReenteringInProgress() {
        assertThatThrownBy(() -> UnderwritingAttemptLifecycle.assertTransition(
                UnderwritingAttemptStatus.APPROVED, UnderwritingAttemptStatus.IN_PROGRESS))
                .isInstanceOf(IllegalUnderwritingAttemptTransitionException.class);
    }

    @Test
    void terminalStatesAcceptNoFurtherTransitions() {
        for (UnderwritingAttemptStatus terminal : new UnderwritingAttemptStatus[]{
                UnderwritingAttemptStatus.APPROVED, UnderwritingAttemptStatus.DECLINED, UnderwritingAttemptStatus.REFERRED}) {
            for (UnderwritingAttemptStatus candidate : UnderwritingAttemptStatus.values()) {
                assertThatThrownBy(() -> UnderwritingAttemptLifecycle.assertTransition(terminal, candidate))
                        .isInstanceOf(IllegalUnderwritingAttemptTransitionException.class);
            }
        }
    }
}
