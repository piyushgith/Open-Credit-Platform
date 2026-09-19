package com.opencredit.platform.scoring.model;

/**
 * Fixed, documented risk-grade boundaries on the score scale — unlike {@link ScorecardRule}
 * bands, these are not configurable per scorecard: {@code A >= 750}, {@code B >= 700},
 * {@code C >= 650}, {@code D >= 600}, else {@code F}.
 */
public enum RiskGrade {
    A, B, C, D, F;

    public static RiskGrade fromScore(int score) {
        if (score >= 750) {
            return A;
        }
        if (score >= 700) {
            return B;
        }
        if (score >= 650) {
            return C;
        }
        if (score >= 600) {
            return D;
        }
        return F;
    }
}
