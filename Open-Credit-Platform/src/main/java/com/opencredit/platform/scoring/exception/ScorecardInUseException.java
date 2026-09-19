package com.opencredit.platform.scoring.exception;

import java.util.UUID;

public class ScorecardInUseException extends RuntimeException {

    public ScorecardInUseException(UUID scorecardId, String reason) {
        super("Scorecard " + scorecardId + " cannot be deleted: " + reason);
    }
}
