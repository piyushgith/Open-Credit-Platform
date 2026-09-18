package com.opencredit.platform.underwriting.support;

import com.opencredit.platform.underwriting.exception.IllegalUnderwritingAttemptTransitionException;
import com.opencredit.platform.underwriting.model.UnderwritingAttemptStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Guards the underwriting attempt state machine: {@code IN_PROGRESS -> (APPROVED | DECLINED | REFERRED)},
 * all three terminal. Pure and stateless, like {@link com.opencredit.platform.loan.support.ApplicationLifecycle},
 * so a completed attempt can never be overwritten — only superseded by a new attempt row.
 */
public final class UnderwritingAttemptLifecycle {

    private static final Map<UnderwritingAttemptStatus, Set<UnderwritingAttemptStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(UnderwritingAttemptStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(UnderwritingAttemptStatus.IN_PROGRESS, EnumSet.of(
                UnderwritingAttemptStatus.APPROVED,
                UnderwritingAttemptStatus.DECLINED,
                UnderwritingAttemptStatus.REFERRED
        ));
        ALLOWED_TRANSITIONS.put(UnderwritingAttemptStatus.APPROVED, EnumSet.noneOf(UnderwritingAttemptStatus.class));
        ALLOWED_TRANSITIONS.put(UnderwritingAttemptStatus.DECLINED, EnumSet.noneOf(UnderwritingAttemptStatus.class));
        ALLOWED_TRANSITIONS.put(UnderwritingAttemptStatus.REFERRED, EnumSet.noneOf(UnderwritingAttemptStatus.class));
    }

    private UnderwritingAttemptLifecycle() {
    }

    public static void assertTransition(UnderwritingAttemptStatus from, UnderwritingAttemptStatus to) {
        if (!ALLOWED_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(UnderwritingAttemptStatus.class)).contains(to)) {
            throw new IllegalUnderwritingAttemptTransitionException(from, to);
        }
    }
}
