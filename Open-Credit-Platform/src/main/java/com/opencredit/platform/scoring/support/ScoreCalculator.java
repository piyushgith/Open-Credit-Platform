package com.opencredit.platform.scoring.support;

import com.opencredit.platform.scoring.model.RiskGrade;
import com.opencredit.platform.scoring.model.ScoreFactorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Matches each resolved {@link ScoreFactorCode} input value against its scorecard's bands and
 * sums the matched points onto the scorecard's base score, clamped to its configured range.
 * Pure and stateless — mirrors {@code RatioCalculator}/{@code RiskIndicatorEvaluator} — and
 * decoupled from persistence: it takes lightweight {@link Band} records, not the
 * {@code ScorecardRule} entity.
 *
 * <p>A factor with no input value, or whose value matches no configured band, is simply left
 * out of the result — never defaulted, never thrown — same missing-propagates rule Weeks 4-5
 * established for ratios/risk indicators. Bands are half-open {@code [minValue, maxValue)}; a
 * {@code null} bound is unbounded on that side.
 */
@Component
public class ScoreCalculator {

    /** One band: {@code value} in {@code [minValue, maxValue)} contributes {@code points}. */
    public record Band(BigDecimal minValue, BigDecimal maxValue, int points) {

        boolean matches(BigDecimal value) {
            boolean aboveMin = minValue == null || value.compareTo(minValue) >= 0;
            boolean belowMax = maxValue == null || value.compareTo(maxValue) < 0;
            return aboveMin && belowMax;
        }
    }

    /** One evaluated factor: its resolved value, the matched band's points, and a label. */
    public record FactorOutcome(ScoreFactorCode factorCode, BigDecimal value, int points, String description) {
    }

    public record ScoreResult(int totalScore, RiskGrade riskGrade, List<FactorOutcome> factorOutcomes) {
    }

    public ScoreResult calculate(Map<ScoreFactorCode, BigDecimal> inputs,
                                  Map<ScoreFactorCode, List<Band>> bandsByFactor,
                                  int baseScore, int minScore, int maxScore) {
        List<FactorOutcome> outcomes = new ArrayList<>();
        int total = baseScore;

        for (Map.Entry<ScoreFactorCode, BigDecimal> entry : inputs.entrySet()) {
            ScoreFactorCode factorCode = entry.getKey();
            BigDecimal value = entry.getValue();
            List<Band> bands = bandsByFactor.get(factorCode);
            if (bands == null) {
                continue;
            }
            for (Band band : bands) {
                if (band.matches(value)) {
                    total += band.points();
                    String description = band.points() > 0 ? factorCode.strengthLabel()
                            : band.points() < 0 ? factorCode.riskLabel()
                            : null;
                    outcomes.add(new FactorOutcome(factorCode, value, band.points(), description));
                    break;
                }
            }
        }

        int clamped = Math.max(minScore, Math.min(maxScore, total));
        return new ScoreResult(clamped, RiskGrade.fromScore(clamped), outcomes);
    }
}
