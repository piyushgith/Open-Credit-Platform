package com.opencredit.platform.scoring.exception;

import java.util.UUID;

public class ScorecardNotFoundException extends RuntimeException {

    public ScorecardNotFoundException(UUID scorecardId) {
        super("No scorecard found with id: " + scorecardId);
    }
}
