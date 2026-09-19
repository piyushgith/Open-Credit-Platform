package com.opencredit.platform.scoring.support;

import com.opencredit.platform.scoring.model.RiskGrade;
import com.opencredit.platform.scoring.model.ScoreFactorCode;
import com.opencredit.platform.scoring.support.ScoreCalculator.Band;
import com.opencredit.platform.scoring.support.ScoreCalculator.FactorOutcome;
import com.opencredit.platform.scoring.support.ScoreCalculator.ScoreResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreCalculatorTest {

    private static final int BASE_SCORE = 500;
    private static final int MIN_SCORE = 300;
    private static final int MAX_SCORE = 900;

    private final ScoreCalculator calculator = new ScoreCalculator();

    /** DEBT_TO_EBITDA (lower is better): [-inf,2)=+80, [2,4)=+20, [4,6)=-20, [6,+inf)=-80. */
    private static Map<ScoreFactorCode, List<Band>> debtToEbitdaBands() {
        Map<ScoreFactorCode, List<Band>> bands = new EnumMap<>(ScoreFactorCode.class);
        bands.put(ScoreFactorCode.DEBT_TO_EBITDA, List.of(
                new Band(null, new BigDecimal("2"), 80),
                new Band(new BigDecimal("2"), new BigDecimal("4"), 20),
                new Band(new BigDecimal("4"), new BigDecimal("6"), -20),
                new Band(new BigDecimal("6"), null, -80)));
        return bands;
    }

    /** CURRENT_RATIO with a deliberate zero-point neutral band in the middle. */
    private static Map<ScoreFactorCode, List<Band>> currentRatioBandsWithNeutralMiddle() {
        Map<ScoreFactorCode, List<Band>> bands = new EnumMap<>(ScoreFactorCode.class);
        bands.put(ScoreFactorCode.CURRENT_RATIO, List.of(
                new Band(null, new BigDecimal("1.0"), -40),
                new Band(new BigDecimal("1.0"), new BigDecimal("1.5"), 0),
                new Band(new BigDecimal("1.5"), null, 40)));
        return bands;
    }

    /** DECLINING_REVENUE encoded as 1 (triggered) / 0 (not triggered). */
    private static Map<ScoreFactorCode, List<Band>> decliningRevenueBands() {
        Map<ScoreFactorCode, List<Band>> bands = new EnumMap<>(ScoreFactorCode.class);
        bands.put(ScoreFactorCode.DECLINING_REVENUE, List.of(
                new Band(BigDecimal.ZERO, BigDecimal.ONE, 40),
                new Band(BigDecimal.ONE, new BigDecimal("2"), -40)));
        return bands;
    }

    @Test
    void matchedBandPointsAreAddedToBaseScore() {
        Map<ScoreFactorCode, BigDecimal> inputs = new EnumMap<>(ScoreFactorCode.class);
        inputs.put(ScoreFactorCode.DEBT_TO_EBITDA, new BigDecimal("3.0"));

        ScoreResult result = calculator.calculate(inputs, debtToEbitdaBands(), BASE_SCORE, MIN_SCORE, MAX_SCORE);

        assertThat(result.totalScore()).isEqualTo(520);
        assertThat(result.factorOutcomes()).hasSize(1);
        FactorOutcome outcome = result.factorOutcomes().get(0);
        assertThat(outcome.factorCode()).isEqualTo(ScoreFactorCode.DEBT_TO_EBITDA);
        assertThat(outcome.points()).isEqualTo(20);
        assertThat(outcome.description()).isEqualTo(ScoreFactorCode.DEBT_TO_EBITDA.strengthLabel());
    }

    @Test
    void bandBoundariesAreHalfOpenLowerInclusiveUpperExclusive() {
        Map<ScoreFactorCode, List<Band>> bands = debtToEbitdaBands();

        Map<ScoreFactorCode, BigDecimal> atBoundary = new EnumMap<>(ScoreFactorCode.class);
        atBoundary.put(ScoreFactorCode.DEBT_TO_EBITDA, new BigDecimal("2.00"));
        Map<ScoreFactorCode, BigDecimal> justBelow = new EnumMap<>(ScoreFactorCode.class);
        justBelow.put(ScoreFactorCode.DEBT_TO_EBITDA, new BigDecimal("1.99"));

        ScoreResult atResult = calculator.calculate(atBoundary, bands, BASE_SCORE, MIN_SCORE, MAX_SCORE);
        ScoreResult belowResult = calculator.calculate(justBelow, bands, BASE_SCORE, MIN_SCORE, MAX_SCORE);

        assertThat(atResult.factorOutcomes().get(0).points()).isEqualTo(20);
        assertThat(belowResult.factorOutcomes().get(0).points()).isEqualTo(80);
    }

    @Test
    void negativePointsUseTheRiskLabel() {
        Map<ScoreFactorCode, BigDecimal> inputs = new EnumMap<>(ScoreFactorCode.class);
        inputs.put(ScoreFactorCode.DEBT_TO_EBITDA, new BigDecimal("7.0"));

        ScoreResult result = calculator.calculate(inputs, debtToEbitdaBands(), BASE_SCORE, MIN_SCORE, MAX_SCORE);

        FactorOutcome outcome = result.factorOutcomes().get(0);
        assertThat(outcome.points()).isEqualTo(-80);
        assertThat(outcome.description()).isEqualTo(ScoreFactorCode.DEBT_TO_EBITDA.riskLabel());
    }

    @Test
    void zeroPointBandProducesNoDescription() {
        Map<ScoreFactorCode, BigDecimal> inputs = new EnumMap<>(ScoreFactorCode.class);
        inputs.put(ScoreFactorCode.CURRENT_RATIO, new BigDecimal("1.2"));

        ScoreResult result = calculator.calculate(
                inputs, currentRatioBandsWithNeutralMiddle(), BASE_SCORE, MIN_SCORE, MAX_SCORE);

        FactorOutcome outcome = result.factorOutcomes().get(0);
        assertThat(outcome.points()).isEqualTo(0);
        assertThat(outcome.description()).isNull();
        assertThat(result.totalScore()).isEqualTo(BASE_SCORE);
    }

    @Test
    void decliningRevenueEncodedAsOneOrZeroPicksTheRightBand() {
        Map<ScoreFactorCode, List<Band>> bands = decliningRevenueBands();

        Map<ScoreFactorCode, BigDecimal> triggered = new EnumMap<>(ScoreFactorCode.class);
        triggered.put(ScoreFactorCode.DECLINING_REVENUE, BigDecimal.ONE);
        Map<ScoreFactorCode, BigDecimal> notTriggered = new EnumMap<>(ScoreFactorCode.class);
        notTriggered.put(ScoreFactorCode.DECLINING_REVENUE, BigDecimal.ZERO);

        ScoreResult triggeredResult = calculator.calculate(triggered, bands, BASE_SCORE, MIN_SCORE, MAX_SCORE);
        ScoreResult notTriggeredResult = calculator.calculate(notTriggered, bands, BASE_SCORE, MIN_SCORE, MAX_SCORE);

        assertThat(triggeredResult.factorOutcomes().get(0).points()).isEqualTo(-40);
        assertThat(notTriggeredResult.factorOutcomes().get(0).points()).isEqualTo(40);
    }

    @Test
    void factorWithNoInputIsSkippedEntirely() {
        ScoreResult result = calculator.calculate(Map.of(), debtToEbitdaBands(), BASE_SCORE, MIN_SCORE, MAX_SCORE);

        assertThat(result.factorOutcomes()).isEmpty();
        assertThat(result.totalScore()).isEqualTo(BASE_SCORE);
    }

    @Test
    void valueMatchingNoConfiguredBandIsSkipped() {
        Map<ScoreFactorCode, List<Band>> bands = new EnumMap<>(ScoreFactorCode.class);
        bands.put(ScoreFactorCode.CURRENT_RATIO, List.of(new Band(new BigDecimal("1.0"), new BigDecimal("2.0"), 40)));

        Map<ScoreFactorCode, BigDecimal> inputs = new EnumMap<>(ScoreFactorCode.class);
        inputs.put(ScoreFactorCode.CURRENT_RATIO, new BigDecimal("5.0"));

        ScoreResult result = calculator.calculate(inputs, bands, BASE_SCORE, MIN_SCORE, MAX_SCORE);

        assertThat(result.factorOutcomes()).isEmpty();
        assertThat(result.totalScore()).isEqualTo(BASE_SCORE);
    }

    @Test
    void totalScoreClampsAtConfiguredMinimum() {
        Map<ScoreFactorCode, List<Band>> bands = new EnumMap<>(ScoreFactorCode.class);
        bands.put(ScoreFactorCode.DEBT_TO_EBITDA, List.of(new Band(null, null, -1000)));

        Map<ScoreFactorCode, BigDecimal> inputs = new EnumMap<>(ScoreFactorCode.class);
        inputs.put(ScoreFactorCode.DEBT_TO_EBITDA, new BigDecimal("10"));

        ScoreResult result = calculator.calculate(inputs, bands, BASE_SCORE, MIN_SCORE, MAX_SCORE);

        assertThat(result.totalScore()).isEqualTo(MIN_SCORE);
        assertThat(result.riskGrade()).isEqualTo(RiskGrade.F);
    }

    @Test
    void totalScoreClampsAtConfiguredMaximum() {
        Map<ScoreFactorCode, List<Band>> bands = new EnumMap<>(ScoreFactorCode.class);
        bands.put(ScoreFactorCode.DEBT_TO_EBITDA, List.of(new Band(null, null, 1000)));

        Map<ScoreFactorCode, BigDecimal> inputs = new EnumMap<>(ScoreFactorCode.class);
        inputs.put(ScoreFactorCode.DEBT_TO_EBITDA, new BigDecimal("1"));

        ScoreResult result = calculator.calculate(inputs, bands, BASE_SCORE, MIN_SCORE, MAX_SCORE);

        assertThat(result.totalScore()).isEqualTo(MAX_SCORE);
        assertThat(result.riskGrade()).isEqualTo(RiskGrade.A);
    }

    @Test
    void riskGradeBoundariesAreExact() {
        assertThat(RiskGrade.fromScore(599)).isEqualTo(RiskGrade.F);
        assertThat(RiskGrade.fromScore(600)).isEqualTo(RiskGrade.D);
        assertThat(RiskGrade.fromScore(649)).isEqualTo(RiskGrade.D);
        assertThat(RiskGrade.fromScore(650)).isEqualTo(RiskGrade.C);
        assertThat(RiskGrade.fromScore(699)).isEqualTo(RiskGrade.C);
        assertThat(RiskGrade.fromScore(700)).isEqualTo(RiskGrade.B);
        assertThat(RiskGrade.fromScore(749)).isEqualTo(RiskGrade.B);
        assertThat(RiskGrade.fromScore(750)).isEqualTo(RiskGrade.A);
    }

    @Test
    void calculationIsDeterministicAcrossRepeatedCalls() {
        Map<ScoreFactorCode, BigDecimal> inputs = new EnumMap<>(ScoreFactorCode.class);
        inputs.put(ScoreFactorCode.DEBT_TO_EBITDA, new BigDecimal("3.0"));
        Map<ScoreFactorCode, List<Band>> bands = debtToEbitdaBands();

        ScoreResult first = calculator.calculate(inputs, bands, BASE_SCORE, MIN_SCORE, MAX_SCORE);
        ScoreResult second = calculator.calculate(inputs, bands, BASE_SCORE, MIN_SCORE, MAX_SCORE);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void multipleFactorsSumIndependently() {
        Map<ScoreFactorCode, List<Band>> bands = new EnumMap<>(ScoreFactorCode.class);
        bands.putAll(debtToEbitdaBands());
        bands.putAll(decliningRevenueBands());

        Map<ScoreFactorCode, BigDecimal> inputs = new EnumMap<>(ScoreFactorCode.class);
        inputs.put(ScoreFactorCode.DEBT_TO_EBITDA, new BigDecimal("1.0"));
        inputs.put(ScoreFactorCode.DECLINING_REVENUE, BigDecimal.ONE);

        ScoreResult result = calculator.calculate(inputs, bands, BASE_SCORE, MIN_SCORE, MAX_SCORE);

        assertThat(result.totalScore()).isEqualTo(BASE_SCORE + 80 - 40);
        assertThat(result.factorOutcomes()).hasSize(2);
    }
}
