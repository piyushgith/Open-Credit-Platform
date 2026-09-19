package com.opencredit.platform.scoring.exception;

import java.util.UUID;

public class ScoreNotFoundException extends RuntimeException {

    public ScoreNotFoundException(UUID scoreId) {
        super("No score found with id: " + scoreId);
    }
}
