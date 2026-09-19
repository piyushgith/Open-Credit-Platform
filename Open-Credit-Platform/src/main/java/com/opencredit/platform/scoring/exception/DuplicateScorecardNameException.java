package com.opencredit.platform.scoring.exception;

public class DuplicateScorecardNameException extends RuntimeException {

    public DuplicateScorecardNameException(String name) {
        super("A scorecard already exists with name: " + name);
    }
}
