package com.opencredit.platform.scoring.exception;

import java.util.UUID;

public class DuplicateScoreException extends RuntimeException {

    public DuplicateScoreException(UUID analysisRunId, UUID scorecardId) {
        super("Analysis run " + analysisRunId + " has already been scored against scorecard " + scorecardId);
    }
}
