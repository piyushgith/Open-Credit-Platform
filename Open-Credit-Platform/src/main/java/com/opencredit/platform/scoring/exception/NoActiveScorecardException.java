package com.opencredit.platform.scoring.exception;

public class NoActiveScorecardException extends RuntimeException {

    public NoActiveScorecardException() {
        super("No active scorecard is configured");
    }
}
