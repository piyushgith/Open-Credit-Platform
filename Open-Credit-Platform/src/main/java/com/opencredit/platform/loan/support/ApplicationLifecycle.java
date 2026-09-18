package com.opencredit.platform.loan.support;

import com.opencredit.platform.loan.exception.IllegalApplicationTransitionException;
import com.opencredit.platform.loan.model.ApplicationStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Guards the loan application state machine:
 * {@code DRAFT -> SUBMITTED -> UNDERWRITING -> (OFFERED | DECLINED) -> SANCTIONED -> DISBURSED}.
 * Pure and stateless, like {@link EmiCalculator}, so it is unit-testable with no Spring context.
 */
public final class ApplicationLifecycle {

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(ApplicationStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(ApplicationStatus.DRAFT, EnumSet.of(ApplicationStatus.SUBMITTED));
        ALLOWED_TRANSITIONS.put(ApplicationStatus.SUBMITTED, EnumSet.of(ApplicationStatus.UNDERWRITING));
        ALLOWED_TRANSITIONS.put(ApplicationStatus.UNDERWRITING,
                EnumSet.of(ApplicationStatus.OFFERED, ApplicationStatus.DECLINED));
        ALLOWED_TRANSITIONS.put(ApplicationStatus.OFFERED, EnumSet.of(ApplicationStatus.SANCTIONED));
        ALLOWED_TRANSITIONS.put(ApplicationStatus.SANCTIONED, EnumSet.of(ApplicationStatus.DISBURSED));
        ALLOWED_TRANSITIONS.put(ApplicationStatus.DECLINED, EnumSet.noneOf(ApplicationStatus.class));
        ALLOWED_TRANSITIONS.put(ApplicationStatus.DISBURSED, EnumSet.noneOf(ApplicationStatus.class));
    }

    private ApplicationLifecycle() {
    }

    public static void assertTransition(ApplicationStatus from, ApplicationStatus to) {
        if (!ALLOWED_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(ApplicationStatus.class)).contains(to)) {
            throw new IllegalApplicationTransitionException(from, to);
        }
    }
}
