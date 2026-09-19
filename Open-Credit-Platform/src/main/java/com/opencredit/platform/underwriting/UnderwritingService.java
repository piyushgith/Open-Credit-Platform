package com.opencredit.platform.underwriting;

import com.opencredit.platform.audit.AuditService;
import com.opencredit.platform.audit.model.AuditEventType;
import com.opencredit.platform.loan.exception.LoanApplicationNotFoundException;
import com.opencredit.platform.loan.model.ApplicationStatus;
import com.opencredit.platform.loan.model.LoanApplication;
import com.opencredit.platform.loan.repository.LoanApplicationRepository;
import com.opencredit.platform.underwriting.dto.UnderwritingAttemptResponse;
import com.opencredit.platform.underwriting.dto.UnderwritingCaseResponse;
import com.opencredit.platform.underwriting.exception.InvalidUnderwritingStateException;
import com.opencredit.platform.underwriting.exception.UnderwritingCaseNotFoundException;
import com.opencredit.platform.underwriting.model.UnderwritingAttempt;
import com.opencredit.platform.underwriting.model.UnderwritingAttemptStatus;
import com.opencredit.platform.underwriting.model.UnderwritingCase;
import com.opencredit.platform.underwriting.repository.UnderwritingAttemptRepository;
import com.opencredit.platform.underwriting.repository.UnderwritingCaseRepository;
import com.opencredit.platform.underwriting.support.UnderwritingAttemptLifecycle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the {@link UnderwritingCase}/{@link UnderwritingAttempt} audit trail. Orchestrating a
 * decisioning run (which strategy to call, how the outcome affects {@link LoanApplication}
 * status) stays in {@code LoanApplicationService}; this service only starts and completes
 * attempts around that run.
 */
@Service
@Transactional
public class UnderwritingService {

    private final UnderwritingCaseRepository caseRepository;
    private final UnderwritingAttemptRepository attemptRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final AuditService auditService;

    public UnderwritingService(UnderwritingCaseRepository caseRepository,
                                UnderwritingAttemptRepository attemptRepository,
                                LoanApplicationRepository loanApplicationRepository,
                                AuditService auditService) {
        this.caseRepository = caseRepository;
        this.attemptRepository = attemptRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.auditService = auditService;
    }

    /**
     * Starts a new attempt for the given application, which must already be {@code UNDERWRITING}.
     * Deactivates (and flushes) any previously active attempt first, so the insert of the new
     * active attempt never collides with the database's one-active-attempt-per-case constraint.
     */
    public UnderwritingAttempt startAttempt(UUID applicationId, ApplicationStatus applicationStatus) {
        if (applicationStatus != ApplicationStatus.UNDERWRITING) {
            throw new InvalidUnderwritingStateException(applicationStatus);
        }

        UnderwritingCase underwritingCase = caseRepository.findByApplicationId(applicationId)
                .orElseGet(() -> caseRepository.save(UnderwritingCase.builder()
                        .id(UUID.randomUUID())
                        .applicationId(applicationId)
                        .createdAt(Instant.now())
                        .build()));

        attemptRepository.findByUnderwritingCaseIdAndActiveTrue(underwritingCase.getId())
                .ifPresent(previous -> {
                    previous.setActive(false);
                    attemptRepository.saveAndFlush(previous);
                });

        int nextCycle = (int) attemptRepository.countByUnderwritingCaseId(underwritingCase.getId()) + 1;

        UnderwritingAttempt attempt = UnderwritingAttempt.builder()
                .id(UUID.randomUUID())
                .underwritingCaseId(underwritingCase.getId())
                .cycleNumber(nextCycle)
                .status(UnderwritingAttemptStatus.IN_PROGRESS)
                .active(true)
                .startedAt(Instant.now())
                .build();
        UnderwritingAttempt saved = attemptRepository.save(attempt);
        auditService.recordEvent(AuditEventType.UNDERWRITING_STARTED, "UnderwritingAttempt", saved.getId(),
                "Cycle " + nextCycle + " started for application " + applicationId);
        return saved;
    }

    public UnderwritingAttempt completeAttempt(UUID attemptId, UnderwritingAttemptStatus outcome,
                                                Map<String, Object> decisionDetails) {
        UnderwritingAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalStateException("Underwriting attempt not found: " + attemptId));
        UnderwritingAttemptStatus previousStatus = attempt.getStatus();
        UnderwritingAttemptLifecycle.assertTransition(attempt.getStatus(), outcome);
        attempt.setStatus(outcome);
        attempt.setDecisionDetails(decisionDetails);
        attempt.setCompletedAt(Instant.now());
        UnderwritingAttempt saved = attemptRepository.save(attempt);
        auditService.recordDataChange("UnderwritingAttempt", saved.getId(), "status", previousStatus, outcome);
        auditService.recordEvent(AuditEventType.UNDERWRITING_COMPLETED, "UnderwritingAttempt", saved.getId(),
                "Cycle " + saved.getCycleNumber() + " completed with outcome " + outcome);
        return saved;
    }

    @Transactional(readOnly = true)
    public UnderwritingCaseResponse getCaseByReference(String referenceNumber) {
        LoanApplication application = loanApplicationRepository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new LoanApplicationNotFoundException(referenceNumber));
        UnderwritingCase underwritingCase = caseRepository.findByApplicationId(application.getId())
                .orElseThrow(() -> new UnderwritingCaseNotFoundException(referenceNumber));
        List<UnderwritingAttempt> attempts =
                attemptRepository.findAllByUnderwritingCaseIdOrderByCycleNumberAsc(underwritingCase.getId());

        return UnderwritingCaseResponse.builder()
                .id(underwritingCase.getId())
                .applicationId(underwritingCase.getApplicationId())
                .createdAt(underwritingCase.getCreatedAt())
                .attempts(attempts.stream().map(this::toResponse).toList())
                .build();
    }

    private UnderwritingAttemptResponse toResponse(UnderwritingAttempt attempt) {
        return UnderwritingAttemptResponse.builder()
                .id(attempt.getId())
                .cycleNumber(attempt.getCycleNumber())
                .status(attempt.getStatus())
                .active(attempt.isActive())
                .startedAt(attempt.getStartedAt())
                .completedAt(attempt.getCompletedAt())
                .build();
    }
}
