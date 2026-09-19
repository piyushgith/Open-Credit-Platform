package com.opencredit.platform.scoring.exception;

import com.opencredit.platform.scoring.model.ScoreFactorCode;

public class OverlappingScorecardRuleException extends RuntimeException {

    public OverlappingScorecardRuleException(ScoreFactorCode factorCode) {
        super("A rule band for " + factorCode + " already covers part of this range on this scorecard");
    }
}
