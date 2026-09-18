package com.opencredit.platform.loan.support;

import com.opencredit.platform.loan.exception.IllegalApplicationTransitionException;
import com.opencredit.platform.loan.model.ApplicationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationLifecycleTest {

    @Test
    void allowsTheFullHappyPath() {
        assertThatCode(() -> {
            ApplicationLifecycle.assertTransition(ApplicationStatus.DRAFT, ApplicationStatus.SUBMITTED);
            ApplicationLifecycle.assertTransition(ApplicationStatus.SUBMITTED, ApplicationStatus.UNDERWRITING);
            ApplicationLifecycle.assertTransition(ApplicationStatus.UNDERWRITING, ApplicationStatus.OFFERED);
            ApplicationLifecycle.assertTransition(ApplicationStatus.OFFERED, ApplicationStatus.SANCTIONED);
            ApplicationLifecycle.assertTransition(ApplicationStatus.SANCTIONED, ApplicationStatus.DISBURSED);
        }).doesNotThrowAnyException();
    }

    @Test
    void allowsUnderwritingToDecline() {
        assertThatCode(() -> ApplicationLifecycle.assertTransition(ApplicationStatus.UNDERWRITING, ApplicationStatus.DECLINED))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsSkippingSubmittedAndUnderwriting() {
        assertThatThrownBy(() -> ApplicationLifecycle.assertTransition(ApplicationStatus.DRAFT, ApplicationStatus.UNDERWRITING))
                .isInstanceOf(IllegalApplicationTransitionException.class);
    }

    @Test
    void rejectsSanctioningBeforeAnOffer() {
        assertThatThrownBy(() -> ApplicationLifecycle.assertTransition(ApplicationStatus.UNDERWRITING, ApplicationStatus.SANCTIONED))
                .isInstanceOf(IllegalApplicationTransitionException.class);
    }

    @Test
    void rejectsMovingBackwards() {
        assertThatThrownBy(() -> ApplicationLifecycle.assertTransition(ApplicationStatus.OFFERED, ApplicationStatus.DRAFT))
                .isInstanceOf(IllegalApplicationTransitionException.class);
    }

    @Test
    void terminalStatesAcceptNoFurtherTransitions() {
        for (ApplicationStatus terminal : new ApplicationStatus[]{ApplicationStatus.DECLINED, ApplicationStatus.DISBURSED}) {
            for (ApplicationStatus candidate : ApplicationStatus.values()) {
                assertThatThrownBy(() -> ApplicationLifecycle.assertTransition(terminal, candidate))
                        .isInstanceOf(IllegalApplicationTransitionException.class);
            }
        }
    }
}
