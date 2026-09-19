package com.opencredit.platform.financial;

import com.opencredit.platform.financial.dto.DerivedFinancialFactResponse;
import com.opencredit.platform.financial.dto.FinancialAnalysisRunResponse;
import com.opencredit.platform.financial.dto.FinancialLineItemResponse;
import com.opencredit.platform.financial.dto.FinancialPeriodRequest;
import com.opencredit.platform.financial.dto.FinancialPeriodResponse;
import com.opencredit.platform.financial.dto.FinancialRatioResponse;
import com.opencredit.platform.financial.dto.FinancialStatementRequest;
import com.opencredit.platform.financial.dto.FinancialStatementResponse;
import com.opencredit.platform.financial.dto.RiskIndicatorResponse;
import com.opencredit.platform.financial.exception.FinancialStatementNotFoundException;
import com.opencredit.platform.financial.exception.InvalidFinancialPeriodException;
import com.opencredit.platform.financial.model.DerivedFinancialFact;
import com.opencredit.platform.financial.model.DerivedFinancialFactCode;
import com.opencredit.platform.financial.model.FinancialAnalysisRun;
import com.opencredit.platform.financial.model.FinancialLineItem;
import com.opencredit.platform.financial.model.FinancialLineItemCode;
import com.opencredit.platform.financial.model.FinancialPeriod;
import com.opencredit.platform.financial.model.FinancialRatio;
import com.opencredit.platform.financial.model.FinancialRatioCode;
import com.opencredit.platform.financial.model.FinancialStatement;
import com.opencredit.platform.financial.model.RiskIndicator;
import com.opencredit.platform.financial.model.RiskIndicatorCode;
import com.opencredit.platform.financial.repository.DerivedFinancialFactRepository;
import com.opencredit.platform.financial.repository.FinancialAnalysisRunRepository;
import com.opencredit.platform.financial.repository.FinancialLineItemRepository;
import com.opencredit.platform.financial.repository.FinancialPeriodRepository;
import com.opencredit.platform.financial.repository.FinancialRatioRepository;
import com.opencredit.platform.financial.repository.FinancialStatementRepository;
import com.opencredit.platform.financial.repository.RiskIndicatorRepository;
import com.opencredit.platform.financial.support.DerivedFactCalculator;
import com.opencredit.platform.financial.support.RatioCalculator;
import com.opencredit.platform.financial.support.RiskIndicatorEvaluator;
import com.opencredit.platform.financial.support.RiskIndicatorEvaluator.PreviousPeriodFacts;
import com.opencredit.platform.loan.exception.LoanApplicationNotFoundException;
import com.opencredit.platform.loan.model.LoanApplication;
import com.opencredit.platform.loan.repository.LoanApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Owns {@link FinancialStatement}/{@link FinancialPeriod}/{@link FinancialLineItem} plus the
 * {@link FinancialAnalysisRun}/{@link DerivedFinancialFact} audit trail produced by
 * {@link DerivedFactCalculator} — mirrors {@code UnderwritingService} owning both the case and
 * its attempts.
 */
@Service
@Transactional
public class FinancialStatementService {

    private final FinancialPeriodRepository periodRepository;
    private final FinancialStatementRepository statementRepository;
    private final FinancialLineItemRepository lineItemRepository;
    private final FinancialAnalysisRunRepository runRepository;
    private final DerivedFinancialFactRepository factRepository;
    private final FinancialRatioRepository ratioRepository;
    private final RiskIndicatorRepository riskIndicatorRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final DerivedFactCalculator calculator;
    private final RatioCalculator ratioCalculator;
    private final RiskIndicatorEvaluator riskIndicatorEvaluator;

    public FinancialStatementService(FinancialPeriodRepository periodRepository,
                                      FinancialStatementRepository statementRepository,
                                      FinancialLineItemRepository lineItemRepository,
                                      FinancialAnalysisRunRepository runRepository,
                                      DerivedFinancialFactRepository factRepository,
                                      FinancialRatioRepository ratioRepository,
                                      RiskIndicatorRepository riskIndicatorRepository,
                                      LoanApplicationRepository loanApplicationRepository,
                                      DerivedFactCalculator calculator,
                                      RatioCalculator ratioCalculator,
                                      RiskIndicatorEvaluator riskIndicatorEvaluator) {
        this.periodRepository = periodRepository;
        this.statementRepository = statementRepository;
        this.lineItemRepository = lineItemRepository;
        this.runRepository = runRepository;
        this.factRepository = factRepository;
        this.ratioRepository = ratioRepository;
        this.riskIndicatorRepository = riskIndicatorRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.calculator = calculator;
        this.ratioCalculator = ratioCalculator;
        this.riskIndicatorEvaluator = riskIndicatorEvaluator;
    }

    public FinancialStatementResponse submit(String referenceNumber, FinancialStatementRequest request) {
        LoanApplication application = getApplication(referenceNumber);
        assertValidPeriod(request.getPeriod());

        FinancialPeriod period = periodRepository.save(FinancialPeriod.builder()
                .id(UUID.randomUUID())
                .periodLabel(request.getPeriod().getPeriodLabel())
                .periodType(request.getPeriod().getPeriodType())
                .startDate(request.getPeriod().getStartDate())
                .endDate(request.getPeriod().getEndDate())
                .createdAt(Instant.now())
                .build());

        FinancialStatement statement = statementRepository.save(FinancialStatement.builder()
                .id(UUID.randomUUID())
                .applicationId(application.getId())
                .periodId(period.getId())
                .submittedAt(Instant.now())
                .createdAt(Instant.now())
                .build());

        List<FinancialLineItem> lineItems = request.getLineItems().stream()
                .map(item -> FinancialLineItem.builder()
                        .id(UUID.randomUUID())
                        .statementId(statement.getId())
                        .lineItemCode(item.getLineItemCode())
                        .value(item.getValue())
                        .createdAt(Instant.now())
                        .build())
                .toList();
        lineItemRepository.saveAll(lineItems);

        return toStatementResponse(statement, period, lineItems);
    }

    @Transactional(readOnly = true)
    public List<FinancialStatementResponse> list(String referenceNumber) {
        LoanApplication application = getApplication(referenceNumber);
        return statementRepository.findAllByApplicationId(application.getId()).stream()
                .map(this::toStatementResponseWithChildren)
                .toList();
    }

    @Transactional(readOnly = true)
    public FinancialStatementResponse get(String referenceNumber, UUID statementId) {
        return toStatementResponseWithChildren(getStatement(referenceNumber, statementId));
    }

    public FinancialAnalysisRunResponse analyze(String referenceNumber, UUID statementId) {
        FinancialStatement statement = getStatement(referenceNumber, statementId);
        List<FinancialLineItem> lineItems = lineItemRepository.findAllByStatementId(statement.getId());

        Map<FinancialLineItemCode, BigDecimal> rawValues = lineItems.stream()
                .collect(Collectors.toMap(FinancialLineItem::getLineItemCode, FinancialLineItem::getValue));
        Map<DerivedFinancialFactCode, BigDecimal> facts = calculator.calculate(rawValues);
        Map<FinancialRatioCode, BigDecimal> ratios = ratioCalculator.calculate(rawValues, facts);
        PreviousPeriodFacts previousPeriod = previousPeriodComparison(statement);
        Map<RiskIndicatorCode, Boolean> riskIndicators =
                riskIndicatorEvaluator.evaluate(ratios, facts, rawValues, previousPeriod);

        FinancialAnalysisRun run = runRepository.save(FinancialAnalysisRun.builder()
                .id(UUID.randomUUID())
                .statementId(statement.getId())
                .runAt(Instant.now())
                .build());

        List<DerivedFinancialFact> factRows = facts.entrySet().stream()
                .map(entry -> DerivedFinancialFact.builder()
                        .id(UUID.randomUUID())
                        .analysisRunId(run.getId())
                        .factCode(entry.getKey())
                        .value(entry.getValue())
                        .createdAt(Instant.now())
                        .build())
                .toList();
        factRepository.saveAll(factRows);

        List<FinancialRatio> ratioRows = ratios.entrySet().stream()
                .map(entry -> FinancialRatio.builder()
                        .id(UUID.randomUUID())
                        .analysisRunId(run.getId())
                        .ratioCode(entry.getKey())
                        .category(entry.getKey().getCategory())
                        .value(entry.getValue())
                        .createdAt(Instant.now())
                        .build())
                .toList();
        ratioRepository.saveAll(ratioRows);

        List<RiskIndicator> riskIndicatorRows = riskIndicators.entrySet().stream()
                .map(entry -> RiskIndicator.builder()
                        .id(UUID.randomUUID())
                        .analysisRunId(run.getId())
                        .indicatorCode(entry.getKey())
                        .triggered(entry.getValue())
                        .createdAt(Instant.now())
                        .build())
                .toList();
        riskIndicatorRepository.saveAll(riskIndicatorRows);

        return toRunResponse(run, factRows, ratioRows, riskIndicatorRows);
    }

    @Transactional(readOnly = true)
    public List<FinancialAnalysisRunResponse> getAnalysisRuns(String referenceNumber, UUID statementId) {
        FinancialStatement statement = getStatement(referenceNumber, statementId);
        return runRepository.findAllByStatementIdOrderByRunAtAsc(statement.getId()).stream()
                .map(run -> toRunResponse(run,
                        factRepository.findAllByAnalysisRunId(run.getId()),
                        ratioRepository.findAllByAnalysisRunId(run.getId()),
                        riskIndicatorRepository.findAllByAnalysisRunId(run.getId())))
                .toList();
    }

    /**
     * Finds the immediately preceding financial period for the same loan application (by
     * {@code endDate}, excluding the statement being analyzed) and returns its raw revenue plus
     * its most recent analysis run's EBITDA fact, if any. Empty fields when there is no earlier
     * statement or it has never been analyzed — the two comparative risk indicators then simply
     * aren't evaluated, same as any other missing input.
     */
    private PreviousPeriodFacts previousPeriodComparison(FinancialStatement statement) {
        List<FinancialStatement> applicationStatements =
                statementRepository.findAllByApplicationId(statement.getApplicationId());
        Map<UUID, FinancialPeriod> periodsById = periodRepository
                .findAllById(applicationStatements.stream().map(FinancialStatement::getPeriodId).toList())
                .stream()
                .collect(Collectors.toMap(FinancialPeriod::getId, period -> period));
        FinancialPeriod currentPeriod = periodsById.get(statement.getPeriodId());

        Optional<FinancialStatement> previousStatement = applicationStatements.stream()
                .filter(other -> !other.getId().equals(statement.getId()))
                .filter(other -> periodsById.get(other.getPeriodId()).getEndDate().isBefore(currentPeriod.getEndDate()))
                .max(Comparator.comparing(other -> periodsById.get(other.getPeriodId()).getEndDate()));

        if (previousStatement.isEmpty()) {
            return PreviousPeriodFacts.none();
        }

        Optional<BigDecimal> previousRevenue = lineItemRepository.findAllByStatementId(previousStatement.get().getId())
                .stream()
                .filter(item -> item.getLineItemCode() == FinancialLineItemCode.REVENUE)
                .map(FinancialLineItem::getValue)
                .findFirst();

        List<FinancialAnalysisRun> previousRuns =
                runRepository.findAllByStatementIdOrderByRunAtAsc(previousStatement.get().getId());
        Optional<BigDecimal> previousEbitda = previousRuns.isEmpty()
                ? Optional.empty()
                : factRepository.findAllByAnalysisRunId(previousRuns.getLast().getId()).stream()
                        .filter(fact -> fact.getFactCode() == DerivedFinancialFactCode.EBITDA)
                        .map(DerivedFinancialFact::getValue)
                        .findFirst();

        return new PreviousPeriodFacts(previousRevenue, previousEbitda);
    }

    private void assertValidPeriod(FinancialPeriodRequest period) {
        if (!period.getStartDate().isBefore(period.getEndDate())) {
            throw new InvalidFinancialPeriodException(period.getStartDate(), period.getEndDate());
        }
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

    private FinancialStatementResponse toStatementResponseWithChildren(FinancialStatement statement) {
        FinancialPeriod period = periodRepository.findById(statement.getPeriodId())
                .orElseThrow(() -> new IllegalStateException("Financial period not found: " + statement.getPeriodId()));
        List<FinancialLineItem> lineItems = lineItemRepository.findAllByStatementId(statement.getId());
        return toStatementResponse(statement, period, lineItems);
    }

    private FinancialStatementResponse toStatementResponse(FinancialStatement statement, FinancialPeriod period,
                                                             List<FinancialLineItem> lineItems) {
        return FinancialStatementResponse.builder()
                .id(statement.getId())
                .applicationId(statement.getApplicationId())
                .period(FinancialPeriodResponse.builder()
                        .id(period.getId())
                        .periodLabel(period.getPeriodLabel())
                        .periodType(period.getPeriodType())
                        .startDate(period.getStartDate())
                        .endDate(period.getEndDate())
                        .build())
                .lineItems(lineItems.stream()
                        .map(item -> FinancialLineItemResponse.builder()
                                .id(item.getId())
                                .lineItemCode(item.getLineItemCode())
                                .value(item.getValue())
                                .build())
                        .toList())
                .submittedAt(statement.getSubmittedAt())
                .build();
    }

    private FinancialAnalysisRunResponse toRunResponse(FinancialAnalysisRun run, List<DerivedFinancialFact> facts,
                                                         List<FinancialRatio> ratios,
                                                         List<RiskIndicator> riskIndicators) {
        return FinancialAnalysisRunResponse.builder()
                .id(run.getId())
                .statementId(run.getStatementId())
                .runAt(run.getRunAt())
                .facts(facts.stream()
                        .map(fact -> DerivedFinancialFactResponse.builder()
                                .id(fact.getId())
                                .factCode(fact.getFactCode())
                                .value(fact.getValue())
                                .build())
                        .toList())
                .ratios(ratios.stream()
                        .map(ratio -> FinancialRatioResponse.builder()
                                .id(ratio.getId())
                                .ratioCode(ratio.getRatioCode())
                                .category(ratio.getCategory())
                                .value(ratio.getValue())
                                .build())
                        .toList())
                .riskIndicators(riskIndicators.stream()
                        .map(indicator -> RiskIndicatorResponse.builder()
                                .id(indicator.getId())
                                .indicatorCode(indicator.getIndicatorCode())
                                .triggered(indicator.isTriggered())
                                .build())
                        .toList())
                .build();
    }
}
