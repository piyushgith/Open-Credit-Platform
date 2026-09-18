package com.opencredit.platform.financial;

import com.opencredit.platform.financial.dto.DerivedFinancialFactResponse;
import com.opencredit.platform.financial.dto.FinancialAnalysisRunResponse;
import com.opencredit.platform.financial.dto.FinancialLineItemResponse;
import com.opencredit.platform.financial.dto.FinancialPeriodRequest;
import com.opencredit.platform.financial.dto.FinancialPeriodResponse;
import com.opencredit.platform.financial.dto.FinancialStatementRequest;
import com.opencredit.platform.financial.dto.FinancialStatementResponse;
import com.opencredit.platform.financial.exception.FinancialStatementNotFoundException;
import com.opencredit.platform.financial.exception.InvalidFinancialPeriodException;
import com.opencredit.platform.financial.model.DerivedFinancialFact;
import com.opencredit.platform.financial.model.DerivedFinancialFactCode;
import com.opencredit.platform.financial.model.FinancialAnalysisRun;
import com.opencredit.platform.financial.model.FinancialLineItem;
import com.opencredit.platform.financial.model.FinancialLineItemCode;
import com.opencredit.platform.financial.model.FinancialPeriod;
import com.opencredit.platform.financial.model.FinancialStatement;
import com.opencredit.platform.financial.repository.DerivedFinancialFactRepository;
import com.opencredit.platform.financial.repository.FinancialAnalysisRunRepository;
import com.opencredit.platform.financial.repository.FinancialLineItemRepository;
import com.opencredit.platform.financial.repository.FinancialPeriodRepository;
import com.opencredit.platform.financial.repository.FinancialStatementRepository;
import com.opencredit.platform.financial.support.DerivedFactCalculator;
import com.opencredit.platform.loan.exception.LoanApplicationNotFoundException;
import com.opencredit.platform.loan.model.LoanApplication;
import com.opencredit.platform.loan.repository.LoanApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    private final LoanApplicationRepository loanApplicationRepository;
    private final DerivedFactCalculator calculator;

    public FinancialStatementService(FinancialPeriodRepository periodRepository,
                                      FinancialStatementRepository statementRepository,
                                      FinancialLineItemRepository lineItemRepository,
                                      FinancialAnalysisRunRepository runRepository,
                                      DerivedFinancialFactRepository factRepository,
                                      LoanApplicationRepository loanApplicationRepository,
                                      DerivedFactCalculator calculator) {
        this.periodRepository = periodRepository;
        this.statementRepository = statementRepository;
        this.lineItemRepository = lineItemRepository;
        this.runRepository = runRepository;
        this.factRepository = factRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.calculator = calculator;
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

        return toRunResponse(run, factRows);
    }

    @Transactional(readOnly = true)
    public List<FinancialAnalysisRunResponse> getAnalysisRuns(String referenceNumber, UUID statementId) {
        FinancialStatement statement = getStatement(referenceNumber, statementId);
        return runRepository.findAllByStatementIdOrderByRunAtAsc(statement.getId()).stream()
                .map(run -> toRunResponse(run, factRepository.findAllByAnalysisRunId(run.getId())))
                .toList();
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

    private FinancialAnalysisRunResponse toRunResponse(FinancialAnalysisRun run, List<DerivedFinancialFact> facts) {
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
                .build();
    }
}
