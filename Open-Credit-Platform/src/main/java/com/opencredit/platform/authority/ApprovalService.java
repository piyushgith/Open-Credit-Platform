package com.opencredit.platform.authority;

import com.opencredit.platform.authority.dto.ApprovalActionRequest;
import com.opencredit.platform.authority.dto.ApprovalCaseResponse;
import com.opencredit.platform.authority.dto.ApprovalDecisionResponse;
import com.opencredit.platform.authority.exception.ApprovalCaseNotFoundException;
import com.opencredit.platform.authority.exception.ApprovalNotRequiredException;
import com.opencredit.platform.authority.exception.ConcurrentApprovalConflictException;
import com.opencredit.platform.authority.exception.DuplicateApprovalCaseException;
import com.opencredit.platform.authority.exception.InsufficientApprovalAuthorityException;
import com.opencredit.platform.authority.exception.NoMatchingAuthorityMatrixEntryException;
import com.opencredit.platform.authority.exception.SelfApprovalException;
import com.opencredit.platform.authority.model.ApprovalCase;
import com.opencredit.platform.authority.model.ApprovalCaseStatus;
import com.opencredit.platform.authority.model.ApprovalDecision;
import com.opencredit.platform.authority.model.ApprovalLevel;
import com.opencredit.platform.authority.model.ApprovalOutcome;
import com.opencredit.platform.authority.model.ApprovalRole;
import com.opencredit.platform.authority.model.AuthorityMatrixEntry;
import com.opencredit.platform.authority.repository.ApprovalCaseRepository;
import com.opencredit.platform.authority.repository.ApprovalDecisionRepository;
import com.opencredit.platform.authority.repository.AuthorityMatrixEntryRepository;
import com.opencredit.platform.authority.support.ApprovalLifecycle;
import com.opencredit.platform.authority.support.AuthorityMatrixResolver;
import com.opencredit.platform.decision.exception.CreditDecisionNotFoundException;
import com.opencredit.platform.decision.model.CreditDecision;
import com.opencredit.platform.decision.model.DecisionOutcome;
import com.opencredit.platform.decision.repository.CreditDecisionRepository;
import com.opencredit.platform.financial.model.FinancialAnalysisRun;
import com.opencredit.platform.financial.model.FinancialStatement;
import com.opencredit.platform.financial.repository.FinancialAnalysisRunRepository;
import com.opencredit.platform.financial.repository.FinancialStatementRepository;
import com.opencredit.platform.loan.model.LoanApplication;
import com.opencredit.platform.loan.repository.LoanApplicationRepository;
import com.opencredit.platform.scoring.model.Score;
import com.opencredit.platform.scoring.repository.ScoreRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Opens and drives {@link ApprovalCase}s. Resolves {@code (productType, riskGrade, amount)} for a
 * {@code CreditDecision} by walking {@code Score -> FinancialAnalysisRun -> FinancialStatement ->
 * LoanApplication} — the same cross-module-repository chain {@code DecisionService} already walks
 * in the other direction, then feeds it to {@link AuthorityMatrixResolver}.
 *
 * <p>Transaction/concurrency strategy (rules 5-7 of the Week 8 plan): every method here is
 * {@code @Transactional}, so the {@link ApprovalDecision} insert and the {@link ApprovalCase}
 * status update commit or roll back together. {@link ApprovalLifecycle#assertCanAct} is the
 * fast-path guard against replaying an already-completed phase, but the actual safety net under
 * concurrent requests is the database: {@code UNIQUE (approval_case_id, role)} lets only one of
 * two racing same-role requests insert successfully, and {@code ApprovalCase.version} protects the
 * status column from a lost update. A losing insert surfaces here as
 * {@link DataIntegrityViolationException}, translated to {@link ConcurrentApprovalConflictException}.
 */
@Service
@Transactional
public class ApprovalService {

    private final CreditDecisionRepository decisionRepository;
    private final ScoreRepository scoreRepository;
    private final FinancialAnalysisRunRepository runRepository;
    private final FinancialStatementRepository statementRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final AuthorityMatrixEntryRepository matrixEntryRepository;
    private final ApprovalCaseRepository caseRepository;
    private final ApprovalDecisionRepository approvalDecisionRepository;

    public ApprovalService(CreditDecisionRepository decisionRepository, ScoreRepository scoreRepository,
                            FinancialAnalysisRunRepository runRepository,
                            FinancialStatementRepository statementRepository,
                            LoanApplicationRepository loanApplicationRepository,
                            AuthorityMatrixEntryRepository matrixEntryRepository,
                            ApprovalCaseRepository caseRepository,
                            ApprovalDecisionRepository approvalDecisionRepository) {
        this.decisionRepository = decisionRepository;
        this.scoreRepository = scoreRepository;
        this.runRepository = runRepository;
        this.statementRepository = statementRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.matrixEntryRepository = matrixEntryRepository;
        this.caseRepository = caseRepository;
        this.approvalDecisionRepository = approvalDecisionRepository;
    }

    public ApprovalCaseResponse openCase(UUID decisionId) {
        CreditDecision decision = decisionRepository.findById(decisionId)
                .orElseThrow(() -> new CreditDecisionNotFoundException(decisionId));
        if (caseRepository.existsByDecisionId(decisionId)) {
            throw new DuplicateApprovalCaseException(decisionId);
        }
        if (decision.getOutcome() == DecisionOutcome.DECLINE) {
            throw new ApprovalNotRequiredException("outcome is DECLINE");
        }

        LoanContext context = resolveContext(decision);
        List<AuthorityMatrixEntry> entries = matrixEntryRepository.findAllByActiveTrueOrderByMatchOrderAsc();
        ApprovalLevel requiredLevel = AuthorityMatrixResolver
                .resolve(entries, context.application().getProductType(), context.score().getRiskGrade(),
                        context.application().getRequestedAmount())
                .map(AuthorityMatrixEntry::getRequiredLevel)
                .orElseThrow(NoMatchingAuthorityMatrixEntryException::new);

        if (requiredLevel == ApprovalLevel.AUTO) {
            throw new ApprovalNotRequiredException("resolved authority level is AUTO");
        }

        ApprovalCase approvalCase = caseRepository.save(ApprovalCase.builder()
                .id(UUID.randomUUID())
                .decisionId(decisionId)
                .requiredLevel(requiredLevel)
                .status(ApprovalCaseStatus.PENDING_MAKER)
                .createdAt(Instant.now())
                .build());

        return toResponse(approvalCase, List.of());
    }

    public ApprovalCaseResponse recordMakerDecision(UUID caseId, ApprovalActionRequest request) {
        return recordDecision(caseId, ApprovalRole.MAKER, request);
    }

    public ApprovalCaseResponse recordCheckerDecision(UUID caseId, ApprovalActionRequest request) {
        return recordDecision(caseId, ApprovalRole.CHECKER, request);
    }

    @Transactional(readOnly = true)
    public ApprovalCaseResponse getCase(UUID caseId) {
        ApprovalCase approvalCase = getCaseEntity(caseId);
        return toResponse(approvalCase, approvalDecisionRepository.findAllByApprovalCaseIdOrderByDecidedAtAsc(caseId));
    }

    @Transactional(readOnly = true)
    public ApprovalCaseResponse getCaseByDecision(UUID decisionId) {
        ApprovalCase approvalCase = caseRepository.findByDecisionId(decisionId)
                .orElseThrow(() -> ApprovalCaseNotFoundException.byDecisionId(decisionId));
        return toResponse(approvalCase,
                approvalDecisionRepository.findAllByApprovalCaseIdOrderByDecidedAtAsc(approvalCase.getId()));
    }

    private ApprovalCaseResponse recordDecision(UUID caseId, ApprovalRole role, ApprovalActionRequest request) {
        ApprovalCase approvalCase = getCaseEntity(caseId);
        ApprovalLifecycle.assertCanAct(approvalCase.getStatus(), role);

        if (role == ApprovalRole.CHECKER) {
            ApprovalDecision makerDecision =
                    approvalDecisionRepository.findByApprovalCaseIdAndRole(caseId, ApprovalRole.MAKER).orElseThrow();
            if (makerDecision.getActorUsername().equalsIgnoreCase(request.getActorUsername())) {
                throw new SelfApprovalException(request.getActorUsername());
            }
            if (!request.getActorLevel().atLeast(approvalCase.getRequiredLevel())) {
                throw new InsufficientApprovalAuthorityException(request.getActorLevel(), approvalCase.getRequiredLevel());
            }
        }

        ApprovalDecision decisionRow = ApprovalDecision.builder()
                .id(UUID.randomUUID())
                .approvalCaseId(caseId)
                .role(role)
                .actorUsername(request.getActorUsername())
                .actorLevel(request.getActorLevel())
                .outcome(request.getOutcome())
                .comment(request.getComment())
                .decidedAt(Instant.now())
                .build();
        try {
            approvalDecisionRepository.saveAndFlush(decisionRow);
        } catch (DataIntegrityViolationException ex) {
            throw new ConcurrentApprovalConflictException(role);
        }

        approvalCase.setStatus(nextStatus(role, request.getOutcome()));
        caseRepository.save(approvalCase);

        return toResponse(approvalCase, approvalDecisionRepository.findAllByApprovalCaseIdOrderByDecidedAtAsc(caseId));
    }

    private static ApprovalCaseStatus nextStatus(ApprovalRole role, ApprovalOutcome outcome) {
        if (role == ApprovalRole.MAKER) {
            return ApprovalCaseStatus.PENDING_CHECKER;
        }
        return outcome == ApprovalOutcome.APPROVE ? ApprovalCaseStatus.APPROVED : ApprovalCaseStatus.REJECTED;
    }

    private ApprovalCase getCaseEntity(UUID caseId) {
        return caseRepository.findById(caseId).orElseThrow(() -> ApprovalCaseNotFoundException.byId(caseId));
    }

    private record LoanContext(LoanApplication application, Score score) {
    }

    private LoanContext resolveContext(CreditDecision decision) {
        Score score = scoreRepository.findById(decision.getScoreId()).orElseThrow();
        FinancialAnalysisRun run = runRepository.findById(score.getAnalysisRunId()).orElseThrow();
        FinancialStatement statement = statementRepository.findById(run.getStatementId()).orElseThrow();
        LoanApplication application = loanApplicationRepository.findById(statement.getApplicationId()).orElseThrow();
        return new LoanContext(application, score);
    }

    private ApprovalCaseResponse toResponse(ApprovalCase approvalCase, List<ApprovalDecision> decisions) {
        return ApprovalCaseResponse.builder()
                .id(approvalCase.getId())
                .decisionId(approvalCase.getDecisionId())
                .requiredLevel(approvalCase.getRequiredLevel())
                .status(approvalCase.getStatus())
                .decisions(decisions.stream().map(this::toDecisionResponse).toList())
                .createdAt(approvalCase.getCreatedAt())
                .build();
    }

    private ApprovalDecisionResponse toDecisionResponse(ApprovalDecision decision) {
        return ApprovalDecisionResponse.builder()
                .role(decision.getRole())
                .actorUsername(decision.getActorUsername())
                .actorLevel(decision.getActorLevel())
                .outcome(decision.getOutcome())
                .comment(decision.getComment())
                .decidedAt(decision.getDecidedAt())
                .build();
    }
}
