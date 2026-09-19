package com.opencredit.platform.scoring.exception;

public class InvalidScorecardRangeException extends RuntimeException {

    public InvalidScorecardRangeException(int minScore, int baseScore, int maxScore) {
        super("Scorecard range must satisfy minScore <= baseScore <= maxScore, got minScore="
                + minScore + ", baseScore=" + baseScore + ", maxScore=" + maxScore);
    }
}
