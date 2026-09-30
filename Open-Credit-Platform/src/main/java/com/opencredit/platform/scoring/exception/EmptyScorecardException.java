package com.opencredit.platform.scoring.exception;

import java.util.UUID;

public class EmptyScorecardException extends RuntimeException {

    public EmptyScorecardException(UUID scorecardId) {
        super("Scorecard " + scorecardId + " cannot be activated: it has no rules");
    }
}
