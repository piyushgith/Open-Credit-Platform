package com.opencredit.platform.decision;

import com.opencredit.platform.decision.dto.CreditDecisionResponse;
import com.opencredit.platform.decision.dto.RuleResultResponse;
import com.opencredit.platform.decision.exception.DuplicateCreditDecisionException;
import com.opencredit.platform.decision.exception.NoActiveCreditPolicyException;
import com.opencredit.platform.decision.model.CreditDecision;
import com.opencredit.platform.decision.model.CreditPolicy;
import com.opencredit.platform.decision.model.CreditRule;
import com.opencredit.platform.decision.model.CreditRuleFactorCode;
import com.opencredit.platform.decision.model.RuleResult;
import com.opencredit.platform.decision.repository.CreditDecisionRepository;
import com.opencredit.platform.decision.repository.CreditPolicyRepository;
import com.opencredit.platform.decision.repository.CreditRuleRepository;
import com.opencredit.platform.decision.repository.RuleResultRepository;
import com.opencredit.platform.decision.support.DecisionEngine;
import com.opencredit.platform.decision.support.DecisionEngine.DecisionResult;
import com.opencredit.platform.decision.support.DecisionEngine.RuleCondition;
import com.opencredit.platform.decision.support.DecisionEngine.RuleOutcome;
import com.opencredit.platform.financial.exception.FinancialAnalysisRunNotFoundException;
import com.opencredit.platform.financial.exception.FinancialStatementNotFoundException;
import com.opencredit.platform.financial.model.FinancialAnalysisRun;
import com.opencredit.platform.financial.model.FinancialRatio;
import com.opencredit.platform.financial.model.FinancialStatement;
import com.opencredit.platform.financial.model.RiskIndicator;
import com.opencredit.platform.financial.repository.FinancialAnalysisRunRepository;
import com.opencredit.platform.financial.repository.FinancialRatioRepository;
import com.opencredit.platform.financial.repository.FinancialStatementRepository;
import com.opencredit.platform.financial.repository.RiskIndicatorRepository;
import com.opencredit.platform.loan.exception.LoanApplicationNotFoundException;
import com.opencredit.platform.loan.model.LoanApplication;
import com.opencredit.platform.loan.repository.LoanApplicationRepository;
import com.opencredit.platform.scoring.exception.ScoreNotFoundException;
import com.opencredit.platform.scoring.model.RiskGrade;
import com.opencredit.platform.scoring.model.Score;
import com.opencredit.platform.scoring.repository.ScoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Decides a {@link Score} against the active {@link CreditPolicy}. Reads {@code scoring}'s
 * {@code Score} and {@code financial}'s {@code FinancialRatio}/{@code RiskIndicator} rows
 * directly, the same cross-module-repository pattern {@code ScoreService} already uses.
 */
@Service
@Transactional
public class DecisionService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final FinancialStatementRepository statementRepository;
    private final FinancialAnalysisRunRepository runRepository;
    private final FinancialRatioRepository ratioRepository;
    private final RiskIndicatorRepository riskIndicatorRepository;
    private final ScoreRepository scoreRepository;
    private final CreditPolicyRepository policyRepository;
    private final CreditRuleRepository ruleRepository;
    private final CreditDecisionRepository decisionRepository;
    private final RuleResultRepository ruleResultRepository;
    private final DecisionEngine decisionEngine;

    public DecisionService(LoanApplicationRepository loanApplicationRepository,
                            FinancialStatementRepository statementRepository,
                            FinancialAnalysisRunRepository runRepository,
                            FinancialRatioRepository ratioRepository,
                            RiskIndicatorRepository riskIndicatorRepository,
                            ScoreRepository scoreRepository,
                            CreditPolicyRepository policyRepository,
                            CreditRuleRepository ruleRepository,
                            CreditDecisionRepository decisionRepository,
                            RuleResultRepository ruleResultRepository,
                            DecisionEngine decisionEngine) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.statementRepository = statementRepository;
        this.runRepository = runRepository;
        this.ratioRepository = ratioRepository;
        this.riskIndicatorRepository = riskIndicatorRepository;
        this.scoreRepository = scoreRepository;
        this.policyRepository = policyRepository;
        this.ruleRepository = ruleRepository;
        this.decisionRepository = decisionRepository;
        this.ruleResultRepository = ruleResultRepository;
        this.decisionEngine = decisionEngine;
    }

    public CreditDecisionResponse decide(String referenceNumber, UUID statementId, UUID analysisRunId, UUID scoreId) {
        Score score = getScore(referenceNumber, statementId, analysisRunId, scoreId);

        CreditPolicy policy = policyRepository.findByActiveTrue()
                .orElseThrow(NoActiveCreditPolicyException::new);

        if (decisionRepository.existsByScoreIdAndPolicyId(score.getId(), policy.getId())) {
            throw new DuplicateCreditDecisionException(score.getId(), policy.getId());
        }

        Map<CreditRuleFactorCode, BigDecimal> inputs = resolveInputs(score);
        List<RuleCondition> rules = ruleRepository.findAllByPolicyIdOrderByRuleOrderAsc(policy.getId()).stream()
                .map(this::toRuleCondition)
                .toList();

        DecisionResult result = decisionEngine.evaluate(inputs, rules, score.getRiskGrade());

        CreditDecision decision = decisionRepository.save(CreditDecision.builder()
                .id(UUID.randomUUID())
                .scoreId(score.getId())
                .policyId(policy.getId())
                .outcome(result.outcome())
                .requiredAuthority(result.requiredAuthority())
                .createdAt(Instant.now())
                .build());

        List<RuleResult> resultRows = result.ruleOutcomes().stream()
                .map(outcome -> toRuleResult(decision.getId(), outcome))
                .toList();
        ruleResultRepository.saveAll(resultRows);

        return toResponse(policy, decision, resultRows);
    }

    @Transactional(readOnly = true)
    public List<CreditDecisionResponse> list(String referenceNumber, UUID statementId, UUID analysisRunId, UUID scoreId) {
        Score score = getScore(referenceNumber, statementId, analysisRunId, scoreId);

        return decisionRepository.findAllByScoreId(score.getId()).stream()
                .map(decision -> toResponse(
                        policyRepository.findById(decision.getPolicyId()).orElseThrow(),
                        decision,
                        ruleResultRepository.findAllByDecisionId(decision.getId())))
                .toList();
    }

    /**
     * Resolves every {@link CreditRuleFactorCode} this score's analysis run can supply:
     * {@code TOTAL_SCORE}/{@code RISK_GRADE} from the {@link Score} itself, every persisted
     * {@code FinancialRatio}, and every persisted {@code RiskIndicator} (encoded {@code 1}/
     * {@code 0}). A factor with no source row is simply left out — {@link DecisionEngine} then
     * skips any rule that needs it.
     */
    private Map<CreditRuleFactorCode, BigDecimal> resolveInputs(Score score) {
        Map<CreditRuleFactorCode, BigDecimal> inputs = new EnumMap<>(CreditRuleFactorCode.class);
        inputs.put(CreditRuleFactorCode.TOTAL_SCORE, BigDecimal.valueOf(score.getTotalScore()));
        inputs.put(CreditRuleFactorCode.RISK_GRADE, BigDecimal.valueOf(riskGradeOrdinal(score.getRiskGrade())));

        for (FinancialRatio ratio : ratioRepository.findAllByAnalysisRunId(score.getAnalysisRunId())) {
            mapFactorCode(ratio.getRatioCode().name()).ifPresent(factorCode -> inputs.put(factorCode, ratio.getValue()));
        }

        for (RiskIndicator indicator : riskIndicatorRepository.findAllByAnalysisRunId(score.getAnalysisRunId())) {
            mapFactorCode(indicator.getIndicatorCode().name())
                    .ifPresent(factorCode -> inputs.put(factorCode, indicator.isTriggered() ? BigDecimal.ONE : BigDecimal.ZERO));
        }

        return inputs;
    }

    private static Optional<CreditRuleFactorCode> mapFactorCode(String name) {
        try {
            return Optional.of(CreditRuleFactorCode.valueOf(name));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /** {@code A=5, B=4, C=3, D=2, F=1} — higher is better, matching {@code RiskGrade}'s declaration order. */
    private static int riskGradeOrdinal(RiskGrade riskGrade) {
        return RiskGrade.values().length - riskGrade.ordinal();
    }

    private RuleCondition toRuleCondition(CreditRule rule) {
        return new RuleCondition(rule.getFactorCode(), rule.getOperator(), rule.getThresholdValue(), rule.getSeverity());
    }

    private RuleResult toRuleResult(UUID decisionId, RuleOutcome outcome) {
        return RuleResult.builder()
                .id(UUID.randomUUID())
                .decisionId(decisionId)
                .factorCode(outcome.factorCode())
                .operator(outcome.operator())
                .thresholdValue(outcome.thresholdValue())
                .actualValue(outcome.actualValue())
                .severity(outcome.severity())
                .passed(outcome.passed())
                .description(outcome.description())
                .createdAt(Instant.now())
                .build();
    }

    private LoanApplication getApplication(String referenceNumber) {
        return loanApplicationRepository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new LoanApplicationNotFoundException(referenceNumber));
    }

    private FinancialStatement getStatement(String referenceNumber, UUID statementId) {
        LoanApplication application = getApplication(referenceNumber);
        return statementRepository.findByIdAndApplicationId(statementId, application.getId())
                .orElseThrow(() -> new FinancialStatementNotFoundException(statementId));
    }

    private FinancialAnalysisRun getRun(String referenceNumber, UUID statementId, UUID analysisRunId) {
        FinancialStatement statement = getStatement(referenceNumber, statementId);
        return runRepository.findById(analysisRunId)
                .filter(run -> run.getStatementId().equals(statement.getId()))
                .orElseThrow(() -> new FinancialAnalysisRunNotFoundException(analysisRunId));
    }

    private Score getScore(String referenceNumber, UUID statementId, UUID analysisRunId, UUID scoreId) {
        FinancialAnalysisRun run = getRun(referenceNumber, statementId, analysisRunId);
        return scoreRepository.findById(scoreId)
                .filter(score -> score.getAnalysisRunId().equals(run.getId()))
                .orElseThrow(() -> new ScoreNotFoundException(scoreId));
    }

    private CreditDecisionResponse toResponse(CreditPolicy policy, CreditDecision decision, List<RuleResult> results) {
        List<RuleResultResponse> passed = results.stream()
                .filter(RuleResult::isPassed)
                .map(this::toRuleResultResponse)
                .toList();
        List<RuleResultResponse> failed = results.stream()
                .filter(result -> !result.isPassed())
                .map(this::toRuleResultResponse)
                .toList();

        return CreditDecisionResponse.builder()
                .id(decision.getId())
                .scoreId(decision.getScoreId())
                .policyName(policy.getName())
                .outcome(decision.getOutcome())
                .requiredAuthority(decision.getRequiredAuthority())
                .passedRules(passed)
                .failedRules(failed)
                .createdAt(decision.getCreatedAt())
                .build();
    }

    private RuleResultResponse toRuleResultResponse(RuleResult result) {
        return RuleResultResponse.builder()
                .factorCode(result.getFactorCode())
                .operator(result.getOperator())
                .thresholdValue(result.getThresholdValue())
                .actualValue(result.getActualValue())
                .severity(result.getSeverity())
                .description(result.getDescription())
                .build();
    }
}
