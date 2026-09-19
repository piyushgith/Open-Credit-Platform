package com.opencredit.platform.scoring.model;

/**
 * The inputs a {@link Scorecard} can score against: four ratios read directly from
 * {@code FinancialRatio} ({@code DEBT_TO_EBITDA}, {@code INTEREST_COVERAGE}, {@code DSCR},
 * {@code CURRENT_RATIO}), one boolean risk indicator encoded as {@code 1}/{@code 0}
 * ({@code DECLINING_REVENUE}, from {@code RiskIndicator}), and one derived from the loan's
 * JSONB {@code requestDetails} ({@code EMI_TO_INCOME}). Each carries a fixed pair of
 * human-readable labels used when a matched band's points are non-zero.
 */
public enum ScoreFactorCode {
    DEBT_TO_EBITDA("High leverage", "Low leverage"),
    INTEREST_COVERAGE("Weak interest coverage", "Strong interest coverage"),
    DSCR("Weak debt service coverage", "Strong debt service coverage"),
    CURRENT_RATIO("Low liquidity", "Strong liquidity"),
    DECLINING_REVENUE("Declining revenue", "Stable or growing revenue"),
    EMI_TO_INCOME("High existing debt burden", "Low existing debt burden");

    private final String riskLabel;
    private final String strengthLabel;

    ScoreFactorCode(String riskLabel, String strengthLabel) {
        this.riskLabel = riskLabel;
        this.strengthLabel = strengthLabel;
    }

    public String riskLabel() {
        return riskLabel;
    }

    public String strengthLabel() {
        return strengthLabel;
    }
}
