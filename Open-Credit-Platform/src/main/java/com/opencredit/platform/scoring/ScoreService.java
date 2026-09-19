package com.opencredit.platform.scoring;

import com.opencredit.platform.financial.exception.FinancialAnalysisRunNotFoundException;
import com.opencredit.platform.financial.exception.FinancialStatementNotFoundException;
import com.opencredit.platform.financial.model.FinancialAnalysisRun;
import com.opencredit.platform.financial.model.FinancialRatio;
import com.opencredit.platform.financial.model.FinancialRatioCode;
import com.opencredit.platform.financial.model.FinancialStatement;
import com.opencredit.platform.financial.model.RiskIndicatorCode;
import com.opencredit.platform.financial.repository.FinancialAnalysisRunRepository;
import com.opencredit.platform.financial.repository.FinancialRatioRepository;
import com.opencredit.platform.financial.repository.FinancialStatementRepository;
import com.opencredit.platform.financial.repository.RiskIndicatorRepository;
import com.opencredit.platform.loan.exception.LoanApplicationNotFoundException;
import com.opencredit.platform.loan.model.LoanApplication;
import com.opencredit.platform.loan.repository.LoanApplicationRepository;
import com.opencredit.platform.scoring.dto.RiskFactorResponse;
import com.opencredit.platform.scoring.dto.ScoreResponse;
import com.opencredit.platform.scoring.exception.DuplicateScoreException;
import com.opencredit.platform.scoring.exception.NoActiveScorecardException;
import com.opencredit.platform.scoring.model.RiskFactor;
import com.opencredit.platform.scoring.model.Score;
import com.opencredit.platform.scoring.model.ScoreFactorCode;
import com.opencredit.platform.scoring.model.Scorecard;
import com.opencredit.platform.scoring.model.ScorecardRule;
import com.opencredit.platform.scoring.repository.RiskFactorRepository;
import com.opencredit.platform.scoring.repository.ScoreRepository;
import com.opencredit.platform.scoring.repository.ScorecardRepository;
import com.opencredit.platform.scoring.repository.ScorecardRuleRepository;
import com.opencredit.platform.scoring.support.ScoreCalculator;
import com.opencredit.platform.scoring.support.ScoreCalculator.Band;
import com.opencredit.platform.scoring.support.ScoreCalculator.ScoreResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Scores a {@link FinancialAnalysisRun} against the active {@link Scorecard}. Reads
 * {@code financial}'s {@code FinancialRatio}/{@code RiskIndicator} rows and {@code loan}'s
 * {@code LoanApplication} directly, the same cross-module-repository pattern
 * {@code FinancialStatementService} already uses to read {@code LoanApplicationRepository}.
 */
@Service
@Transactional
public class ScoreService {

    private static final int EMI_TO_INCOME_SCALE = 4;

    private final FinancialAnalysisRunRepository runRepository;
    private final FinancialStatementRepository statementRepository;
    private final FinancialRatioRepository ratioRepository;
    private final RiskIndicatorRepository riskIndicatorRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final ScorecardRepository scorecardRepository;
    private final ScorecardRuleRepository ruleRepository;
    private final ScoreRepository scoreRepository;
    private final RiskFactorRepository riskFactorRepository;
    private final ScoreCalculator scoreCalculator;
    private final ObjectMapper objectMapper;

    public ScoreService(FinancialAnalysisRunRepository runRepository,
                         FinancialStatementRepository statementRepository,
                         FinancialRatioRepository ratioRepository,
                         RiskIndicatorRepository riskIndicatorRepository,
                         LoanApplicationRepository loanApplicationRepository,
                         ScorecardRepository scorecardRepository,
                         ScorecardRuleRepository ruleRepository,
                         ScoreRepository scoreRepository,
                         RiskFactorRepository riskFactorRepository,
                         ScoreCalculator scoreCalculator,
                         ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.statementRepository = statementRepository;
        this.ratioRepository = ratioRepository;
        this.riskIndicatorRepository = riskIndicatorRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.scorecardRepository = scorecardRepository;
        this.ruleRepository = ruleRepository;
        this.scoreRepository = scoreRepository;
        this.riskFactorRepository = riskFactorRepository;
        this.scoreCalculator = scoreCalculator;
        this.objectMapper = objectMapper;
    }

    public ScoreResponse score(String referenceNumber, UUID statementId, UUID analysisRunId) {
        FinancialStatement statement = getStatement(referenceNumber, statementId);
        FinancialAnalysisRun run = runRepository.findById(analysisRunId)
                .filter(r -> r.getStatementId().equals(statement.getId()))
                .orElseThrow(() -> new FinancialAnalysisRunNotFoundException(analysisRunId));

        Scorecard scorecard = scorecardRepository.findByActiveTrue()
                .orElseThrow(NoActiveScorecardException::new);

        if (scoreRepository.existsByAnalysisRunIdAndScorecardId(run.getId(), scorecard.getId())) {
            throw new DuplicateScoreException(run.getId(), scorecard.getId());
        }

        Map<ScoreFactorCode, BigDecimal> inputs = resolveInputs(run, statement);
        Map<ScoreFactorCode, List<Band>> bandsByFactor = loadBands(scorecard.getId());

        ScoreResult result = scoreCalculator.calculate(inputs, bandsByFactor,
                scorecard.getBaseScore(), scorecard.getMinScore(), scorecard.getMaxScore());

        Score score = scoreRepository.save(Score.builder()
                .id(UUID.randomUUID())
                .analysisRunId(run.getId())
                .scorecardId(scorecard.getId())
                .totalScore(result.totalScore())
                .riskGrade(result.riskGrade())
                .createdAt(Instant.now())
                .build());

        List<RiskFactor> factorRows = result.factorOutcomes().stream()
                .map(outcome -> RiskFactor.builder()
                        .id(UUID.randomUUID())
                        .scoreId(score.getId())
                        .factorCode(outcome.factorCode())
                        .value(outcome.value())
                        .points(outcome.points())
                        .description(outcome.description())
                        .createdAt(Instant.now())
                        .build())
                .toList();
        riskFactorRepository.saveAll(factorRows);

        return toScoreResponse(scorecard, score, factorRows);
    }

    @Transactional(readOnly = true)
    public List<ScoreResponse> list(String referenceNumber, UUID statementId, UUID analysisRunId) {
        FinancialStatement statement = getStatement(referenceNumber, statementId);
        if (!runRepository.findById(analysisRunId).map(FinancialAnalysisRun::getStatementId)
                .map(id -> id.equals(statement.getId())).orElse(false)) {
            throw new FinancialAnalysisRunNotFoundException(analysisRunId);
        }

        return scoreRepository.findAllByAnalysisRunId(analysisRunId).stream()
                .map(score -> toScoreResponse(
                        scorecardRepository.findById(score.getScorecardId()).orElseThrow(),
                        score,
                        riskFactorRepository.findAllByScoreId(score.getId())))
                .toList();
    }

    /**
     * Resolves every {@link ScoreFactorCode} input this run can supply: the four ratios and the
     * boolean revenue-trend indicator from this analysis run, plus (when present) the
     * EMI-to-income ratio read from the owning {@code LoanApplication}'s JSONB
     * {@code requestDetails}. A factor whose source data is absent is simply left out of the
     * map — {@link ScoreCalculator} then skips it, same as a missing ratio input upstream.
     */
    private Map<ScoreFactorCode, BigDecimal> resolveInputs(FinancialAnalysisRun run, FinancialStatement statement) {
        Map<ScoreFactorCode, BigDecimal> inputs = new EnumMap<>(ScoreFactorCode.class);

        Map<FinancialRatioCode, BigDecimal> ratios = ratioRepository.findAllByAnalysisRunId(run.getId()).stream()
                .collect(Collectors.toMap(FinancialRatio::getRatioCode, FinancialRatio::getValue));
        putIfPresent(inputs, ScoreFactorCode.DEBT_TO_EBITDA, ratios.get(FinancialRatioCode.DEBT_TO_EBITDA));
        putIfPresent(inputs, ScoreFactorCode.INTEREST_COVERAGE, ratios.get(FinancialRatioCode.INTEREST_COVERAGE));
        putIfPresent(inputs, ScoreFactorCode.DSCR, ratios.get(FinancialRatioCode.DSCR));
        putIfPresent(inputs, ScoreFactorCode.CURRENT_RATIO, ratios.get(FinancialRatioCode.CURRENT_RATIO));

        riskIndicatorRepository.findAllByAnalysisRunId(run.getId()).stream()
                .filter(indicator -> indicator.getIndicatorCode() == RiskIndicatorCode.DECLINING_REVENUE)
                .findFirst()
                .ifPresent(indicator -> inputs.put(ScoreFactorCode.DECLINING_REVENUE,
                        indicator.isTriggered() ? BigDecimal.ONE : BigDecimal.ZERO));

        resolveEmiToIncome(statement).ifPresent(value -> inputs.put(ScoreFactorCode.EMI_TO_INCOME, value));

        return inputs;
    }

    /**
     * {@code existingEmi / monthlyIncome} read generically from {@code requestDetails} by key —
     * not gated on {@code productType}, so any product whose request happens to carry both keys
     * is scored on this factor. Empty when either key is missing, unparseable, or
     * {@code monthlyIncome} is not positive.
     */
    private Optional<BigDecimal> resolveEmiToIncome(FinancialStatement statement) {
        LoanApplication application = loanApplicationRepository.findById(statement.getApplicationId())
                .orElseThrow(() -> new IllegalStateException(
                        "Loan application not found for financial statement: " + statement.getId()));

        Map<String, Object> requestDetails = application.getRequestDetails();
        BigDecimal monthlyIncome = readBigDecimal(requestDetails, "monthlyIncome");
        BigDecimal existingEmi = readBigDecimal(requestDetails, "existingEmi");
        if (monthlyIncome == null || existingEmi == null || monthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        return Optional.of(existingEmi.divide(monthlyIncome, EMI_TO_INCOME_SCALE, RoundingMode.HALF_UP));
    }

    private BigDecimal readBigDecimal(Map<String, Object> requestDetails, String key) {
        Object value = requestDetails.get(key);
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(value, BigDecimal.class);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static void putIfPresent(Map<ScoreFactorCode, BigDecimal> inputs, ScoreFactorCode code, BigDecimal value) {
        if (value != null) {
            inputs.put(code, value);
        }
    }

    private Map<ScoreFactorCode, List<Band>> loadBands(UUID scorecardId) {
        return ruleRepository.findAllByScorecardIdOrderByFactorCodeAscBandOrderAsc(scorecardId).stream()
                .collect(Collectors.groupingBy(ScorecardRule::getFactorCode,
                        () -> new EnumMap<>(ScoreFactorCode.class),
                        Collectors.mapping(
                                rule -> new Band(rule.getMinValue(), rule.getMaxValue(), rule.getPoints()),
                                Collectors.toList())));
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

    private ScoreResponse toScoreResponse(Scorecard scorecard, Score score, List<RiskFactor> factors) {
        List<RiskFactorResponse> contributing = factors.stream()
                .filter(factor -> factor.getPoints() > 0)
                .map(this::toFactorResponse)
                .toList();
        List<RiskFactorResponse> failed = factors.stream()
                .filter(factor -> factor.getPoints() < 0)
                .map(this::toFactorResponse)
                .toList();

        return ScoreResponse.builder()
                .id(score.getId())
                .analysisRunId(score.getAnalysisRunId())
                .scorecardName(scorecard.getName())
                .totalScore(score.getTotalScore())
                .riskGrade(score.getRiskGrade())
                .contributingFactors(contributing)
                .failedFactors(failed)
                .createdAt(score.getCreatedAt())
                .build();
    }

    private RiskFactorResponse toFactorResponse(RiskFactor factor) {
        return RiskFactorResponse.builder()
                .factorCode(factor.getFactorCode())
                .value(factor.getValue())
                .points(factor.getPoints())
                .description(factor.getDescription())
                .build();
    }
}
