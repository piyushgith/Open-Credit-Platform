package com.opencredit.platform.decision.support;

import com.opencredit.platform.decision.model.ComparisonOperator;
import com.opencredit.platform.decision.model.CreditRuleFactorCode;
import com.opencredit.platform.decision.model.DecisionOutcome;
import com.opencredit.platform.decision.model.RequiredAuthority;
import com.opencredit.platform.decision.model.RuleSeverity;
import com.opencredit.platform.scoring.model.RiskGrade;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Evaluates every configured {@link RuleCondition} against its resolved input value and derives
 * a {@link DecisionOutcome}. Pure and stateless — mirrors {@code ScoreCalculator} — and decoupled
 * from persistence: it takes lightweight {@link RuleCondition} records, not the
 * {@code CreditRule} entity.
 *
 * <p>A rule's set is implicitly ANDed: a failed {@code HARD} rule forces {@code DECLINE}; a
 * failed {@code SOFT} rule (with every {@code HARD} rule passing) forces {@code REFER}; all rules
 * passing (with at least one evaluated) approves. A rule whose input value is missing is simply
 * left out of the result — never defaulted, never thrown — same missing-propagates rule Weeks
 * 4-6 established. A decision with zero evaluable rules refers rather than approves: deterministic
 * approval requires at least one satisfied rule, never an absence of evidence.
 */
@Component
public class DecisionEngine {

    /** One condition: {@code factorCode operator thresholdValue}, with its failure severity. */
    public record RuleCondition(CreditRuleFactorCode factorCode, ComparisonOperator operator,
                                 BigDecimal thresholdValue, RuleSeverity severity) {
    }

    /** One evaluated rule: its resolved value, whether it passed, and a human-readable reason. */
    public record RuleOutcome(CreditRuleFactorCode factorCode, ComparisonOperator operator,
                               BigDecimal thresholdValue, BigDecimal actualValue, RuleSeverity severity,
                               boolean passed, String description) {
    }

    public record DecisionResult(DecisionOutcome outcome, RequiredAuthority requiredAuthority,
                                  List<RuleOutcome> ruleOutcomes) {
    }

    public DecisionResult evaluate(Map<CreditRuleFactorCode, BigDecimal> inputs, List<RuleCondition> rules,
                                    RiskGrade riskGrade) {
        List<RuleOutcome> outcomes = new ArrayList<>();
        boolean anyHardFailed = false;
        boolean anySoftFailed = false;

        for (RuleCondition rule : rules) {
            BigDecimal actual = inputs.get(rule.factorCode());
            if (actual == null) {
                continue;
            }

            boolean passed = rule.operator().evaluate(actual, rule.thresholdValue());
            if (!passed) {
                if (rule.severity() == RuleSeverity.HARD) {
                    anyHardFailed = true;
                } else {
                    anySoftFailed = true;
                }
            }

            String description = describe(rule.factorCode(), rule.operator(), rule.thresholdValue(), actual, passed);
            outcomes.add(new RuleOutcome(rule.factorCode(), rule.operator(), rule.thresholdValue(), actual,
                    rule.severity(), passed, description));
        }

        DecisionOutcome outcome;
        if (anyHardFailed) {
            outcome = DecisionOutcome.DECLINE;
        } else if (anySoftFailed || outcomes.isEmpty()) {
            outcome = DecisionOutcome.REFER;
        } else {
            outcome = DecisionOutcome.APPROVE;
        }

        return new DecisionResult(outcome, RequiredAuthority.forOutcome(outcome, riskGrade), outcomes);
    }

    private static String describe(CreditRuleFactorCode factorCode, ComparisonOperator operator,
                                     BigDecimal thresholdValue, BigDecimal actualValue, boolean passed) {
        return factorCode + " " + actualValue + (passed ? " satisfies " : " does not satisfy ")
                + operator + " " + thresholdValue;
    }
}
