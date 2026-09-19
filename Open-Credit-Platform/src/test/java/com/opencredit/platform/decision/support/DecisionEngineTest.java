package com.opencredit.platform.decision.support;

import com.opencredit.platform.decision.model.ComparisonOperator;
import com.opencredit.platform.decision.model.CreditRuleFactorCode;
import com.opencredit.platform.decision.model.DecisionOutcome;
import com.opencredit.platform.decision.model.RequiredAuthority;
import com.opencredit.platform.decision.model.RuleSeverity;
import com.opencredit.platform.decision.support.DecisionEngine.DecisionResult;
import com.opencredit.platform.decision.support.DecisionEngine.RuleCondition;
import com.opencredit.platform.decision.support.DecisionEngine.RuleOutcome;
import com.opencredit.platform.scoring.model.RiskGrade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionEngineTest {

    private final DecisionEngine engine = new DecisionEngine();

    private static RuleCondition rule(CreditRuleFactorCode factorCode, ComparisonOperator operator,
                                       String threshold, RuleSeverity severity) {
        return new RuleCondition(factorCode, operator, new BigDecimal(threshold), severity);
    }

    private static Map<CreditRuleFactorCode, BigDecimal> inputs(CreditRuleFactorCode code, String value) {
        Map<CreditRuleFactorCode, BigDecimal> inputs = new EnumMap<>(CreditRuleFactorCode.class);
        inputs.put(code, new BigDecimal(value));
        return inputs;
    }

    @Test
    void allRulesPassingApproves() {
        List<RuleCondition> rules = List.of(
                rule(CreditRuleFactorCode.TOTAL_SCORE, ComparisonOperator.GTE, "700", RuleSeverity.HARD),
                rule(CreditRuleFactorCode.DSCR, ComparisonOperator.GTE, "1.5", RuleSeverity.HARD));
        Map<CreditRuleFactorCode, BigDecimal> inputs = new EnumMap<>(CreditRuleFactorCode.class);
        inputs.put(CreditRuleFactorCode.TOTAL_SCORE, new BigDecimal("750"));
        inputs.put(CreditRuleFactorCode.DSCR, new BigDecimal("2.0"));

        DecisionResult result = engine.evaluate(inputs, rules, RiskGrade.A);

        assertThat(result.outcome()).isEqualTo(DecisionOutcome.APPROVE);
        assertThat(result.requiredAuthority()).isEqualTo(RequiredAuthority.AUTO);
        assertThat(result.ruleOutcomes()).hasSize(2);
        assertThat(result.ruleOutcomes()).allMatch(RuleOutcome::passed);
    }

    @Test
    void aFailedHardRuleDeclinesRegardlessOfOtherPasses() {
        List<RuleCondition> rules = List.of(
                rule(CreditRuleFactorCode.TOTAL_SCORE, ComparisonOperator.GTE, "700", RuleSeverity.HARD),
                rule(CreditRuleFactorCode.DEBT_TO_EBITDA, ComparisonOperator.LTE, "4.0", RuleSeverity.HARD));
        Map<CreditRuleFactorCode, BigDecimal> inputs = new EnumMap<>(CreditRuleFactorCode.class);
        inputs.put(CreditRuleFactorCode.TOTAL_SCORE, new BigDecimal("750"));
        inputs.put(CreditRuleFactorCode.DEBT_TO_EBITDA, new BigDecimal("6.0"));

        DecisionResult result = engine.evaluate(inputs, rules, RiskGrade.A);

        assertThat(result.outcome()).isEqualTo(DecisionOutcome.DECLINE);
        assertThat(result.requiredAuthority()).isEqualTo(RequiredAuthority.AUTO);
    }

    @Test
    void aFailedSoftRuleRefersWhenNoHardRuleFails() {
        List<RuleCondition> rules = List.of(
                rule(CreditRuleFactorCode.TOTAL_SCORE, ComparisonOperator.GTE, "700", RuleSeverity.HARD),
                rule(CreditRuleFactorCode.CURRENT_RATIO, ComparisonOperator.GTE, "1.0", RuleSeverity.SOFT));
        Map<CreditRuleFactorCode, BigDecimal> inputs = new EnumMap<>(CreditRuleFactorCode.class);
        inputs.put(CreditRuleFactorCode.TOTAL_SCORE, new BigDecimal("750"));
        inputs.put(CreditRuleFactorCode.CURRENT_RATIO, new BigDecimal("0.5"));

        DecisionResult result = engine.evaluate(inputs, rules, RiskGrade.B);

        assertThat(result.outcome()).isEqualTo(DecisionOutcome.REFER);
        assertThat(result.requiredAuthority()).isEqualTo(RequiredAuthority.SENIOR_CREDIT_MANAGER);
    }

    @Test
    void aMissingInputSkipsOnlyThatRule() {
        List<RuleCondition> rules = List.of(
                rule(CreditRuleFactorCode.TOTAL_SCORE, ComparisonOperator.GTE, "700", RuleSeverity.HARD),
                rule(CreditRuleFactorCode.DSCR, ComparisonOperator.GTE, "1.5", RuleSeverity.HARD));

        DecisionResult result = engine.evaluate(inputs(CreditRuleFactorCode.TOTAL_SCORE, "750"), rules, RiskGrade.A);

        assertThat(result.ruleOutcomes()).hasSize(1);
        assertThat(result.outcome()).isEqualTo(DecisionOutcome.APPROVE);
    }

    @Test
    void zeroEvaluableRulesRefersRatherThanApproves() {
        List<RuleCondition> rules = List.of(
                rule(CreditRuleFactorCode.TOTAL_SCORE, ComparisonOperator.GTE, "700", RuleSeverity.HARD));

        DecisionResult result = engine.evaluate(Map.of(), rules, RiskGrade.A);

        assertThat(result.ruleOutcomes()).isEmpty();
        assertThat(result.outcome()).isEqualTo(DecisionOutcome.REFER);
    }

    @Test
    void approveWithWeakerRiskGradeRequiresCreditOfficer() {
        List<RuleCondition> rules = List.of(
                rule(CreditRuleFactorCode.TOTAL_SCORE, ComparisonOperator.GTE, "700", RuleSeverity.HARD));

        DecisionResult resultA = engine.evaluate(inputs(CreditRuleFactorCode.TOTAL_SCORE, "750"), rules, RiskGrade.A);
        DecisionResult resultB = engine.evaluate(inputs(CreditRuleFactorCode.TOTAL_SCORE, "750"), rules, RiskGrade.B);
        DecisionResult resultC = engine.evaluate(inputs(CreditRuleFactorCode.TOTAL_SCORE, "750"), rules, RiskGrade.C);
        DecisionResult resultD = engine.evaluate(inputs(CreditRuleFactorCode.TOTAL_SCORE, "750"), rules, RiskGrade.D);
        DecisionResult resultF = engine.evaluate(inputs(CreditRuleFactorCode.TOTAL_SCORE, "750"), rules, RiskGrade.F);

        assertThat(resultA.requiredAuthority()).isEqualTo(RequiredAuthority.AUTO);
        assertThat(resultB.requiredAuthority()).isEqualTo(RequiredAuthority.AUTO);
        assertThat(resultC.requiredAuthority()).isEqualTo(RequiredAuthority.CREDIT_OFFICER);
        assertThat(resultD.requiredAuthority()).isEqualTo(RequiredAuthority.CREDIT_OFFICER);
        assertThat(resultF.requiredAuthority()).isEqualTo(RequiredAuthority.CREDIT_OFFICER);
    }

    @Test
    void declineAlwaysRequiresAutoAuthorityRegardlessOfRiskGrade() {
        List<RuleCondition> rules = List.of(
                rule(CreditRuleFactorCode.TOTAL_SCORE, ComparisonOperator.GTE, "700", RuleSeverity.HARD));

        DecisionResult result = engine.evaluate(inputs(CreditRuleFactorCode.TOTAL_SCORE, "600"), rules, RiskGrade.A);

        assertThat(result.outcome()).isEqualTo(DecisionOutcome.DECLINE);
        assertThat(result.requiredAuthority()).isEqualTo(RequiredAuthority.AUTO);
    }

    @Test
    void riskGradeFactorComparesAsAnOrdinalThreshold() {
        // RISK_GRADE encoded A=5,B=4,C=3,D=2,F=1: rule "RISK_GRADE >= 3" means C or better.
        List<RuleCondition> rules = List.of(
                rule(CreditRuleFactorCode.RISK_GRADE, ComparisonOperator.GTE, "3", RuleSeverity.HARD));

        DecisionResult gradeC = engine.evaluate(inputs(CreditRuleFactorCode.RISK_GRADE, "3"), rules, RiskGrade.C);
        DecisionResult gradeD = engine.evaluate(inputs(CreditRuleFactorCode.RISK_GRADE, "2"), rules, RiskGrade.D);

        assertThat(gradeC.outcome()).isEqualTo(DecisionOutcome.APPROVE);
        assertThat(gradeD.outcome()).isEqualTo(DecisionOutcome.DECLINE);
    }

    @Test
    void decliningRevenueFactorEncodedAsOneOrZero() {
        List<RuleCondition> rules = List.of(
                rule(CreditRuleFactorCode.DECLINING_REVENUE, ComparisonOperator.EQ, "0", RuleSeverity.SOFT));

        DecisionResult triggered = engine.evaluate(inputs(CreditRuleFactorCode.DECLINING_REVENUE, "1"), rules, RiskGrade.A);
        DecisionResult notTriggered = engine.evaluate(inputs(CreditRuleFactorCode.DECLINING_REVENUE, "0"), rules, RiskGrade.A);

        assertThat(triggered.outcome()).isEqualTo(DecisionOutcome.REFER);
        assertThat(notTriggered.outcome()).isEqualTo(DecisionOutcome.APPROVE);
    }

    @Test
    void calculationIsDeterministicAcrossRepeatedCalls() {
        List<RuleCondition> rules = List.of(
                rule(CreditRuleFactorCode.TOTAL_SCORE, ComparisonOperator.GTE, "700", RuleSeverity.HARD));
        Map<CreditRuleFactorCode, BigDecimal> inputs = inputs(CreditRuleFactorCode.TOTAL_SCORE, "750");

        DecisionResult first = engine.evaluate(inputs, rules, RiskGrade.A);
        DecisionResult second = engine.evaluate(inputs, rules, RiskGrade.A);

        assertThat(first).isEqualTo(second);
    }
}
