package com.opencredit.platform.scoring.exception;

import java.util.UUID;

public class ConcurrentScorecardActivationException extends RuntimeException {

    public ConcurrentScorecardActivationException(UUID scorecardId) {
        super("Another scorecard was activated concurrently; retry activating scorecard " + scorecardId);
    }
}
