package com.opencredit.platform.authority;

import com.opencredit.platform.audit.AuditService;
import com.opencredit.platform.audit.model.AuditEventType;
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
import com.opencredit.platform.loan.LoanApplicationService;
import com.opencredit.platform.loan.model.DecisionStatus;
import com.opencredit.platform.loan.model.LoanApplication;
import com.opencredit.platform.loan.repository.LoanApplicationRepository;
import com.opencredit.platform.scoring.model.Score;
import com.opencredit.platform.scoring.repository.ScoreRepository;
import com.opencredit.platform.security.model.AppRole;
import com.opencredit.platform.security.support.AuthenticatedUser;
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
 * in the other direction, then feeds it to {@link AuthorityMatrixResolver}. Also the sole place
 * that finalizes the underlying {@code LoanApplication} once this pipeline's outcome is settled —
 * either immediately in {@link #openCase} (decline, or a resolved {@code AUTO} level needing no
 * case) or once a checker's decision makes a case terminal — via {@code LoanApplicationService},
 * which keeps sole ownership of {@code ApplicationStatus} mutation regardless of which pipeline
 * triggers it.
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
    private final LoanApplicationService loanApplicationService;
    private final AuditService auditService;

    public ApprovalService(CreditDecisionRepository decisionRepository, ScoreRepository scoreRepository,
                            FinancialAnalysisRunRepository runRepository,
                            FinancialStatementRepository statementRepository,
                            LoanApplicationRepository loanApplicationRepository,
                            AuthorityMatrixEntryRepository matrixEntryRepository,
                            ApprovalCaseRepository caseRepository,
                            ApprovalDecisionRepository approvalDecisionRepository,
                            LoanApplicationService loanApplicationService,
                            AuditService auditService) {
        this.decisionRepository = decisionRepository;
        this.scoreRepository = scoreRepository;
        this.runRepository = runRepository;
        this.statementRepository = statementRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.matrixEntryRepository = matrixEntryRepository;
        this.caseRepository = caseRepository;
        this.approvalDecisionRepository = approvalDecisionRepository;
        this.loanApplicationService = loanApplicationService;
        this.auditService = auditService;
    }

    /**
     * Resolves whether this decision needs a human approval case at all and, if not, finalizes the
     * application itself instead of opening one — {@code DecisionService} never does this on its
     * own (see {@link DecisionOutcome}'s Javadoc); this is the one place that resolution happens,
     * since every caller is expected to attempt this endpoint right after a decision is made. The
     * {@code noRollbackFor} is required: {@link ApprovalNotRequiredException} is thrown on the very
     * same "no case needed" paths that just finalized the application, and the default rollback-on-
     * any-{@code RuntimeException} behavior would otherwise discard that finalization.
     */
    @Transactional(noRollbackFor = ApprovalNotRequiredException.class)
    public ApprovalCaseResponse openCase(UUID decisionId) {
        CreditDecision decision = decisionRepository.findById(decisionId)
                .orElseThrow(() -> new CreditDecisionNotFoundException(decisionId));
        if (caseRepository.existsByDecisionId(decisionId)) {
            throw new DuplicateApprovalCaseException(decisionId);
        }

        LoanContext context = resolveContext(decision);

        if (decision.getOutcome() == DecisionOutcome.DECLINE) {
            loanApplicationService.applyCreditPipelineOutcome(context.application().getId(), DecisionStatus.DECLINED);
            throw new ApprovalNotRequiredException("outcome is DECLINE");
        }

        List<AuthorityMatrixEntry> entries = matrixEntryRepository.findAllByActiveTrueOrderByMatchOrderAsc();
        ApprovalLevel requiredLevel = AuthorityMatrixResolver
                .resolve(entries, context.application().getProductType(), context.score().getRiskGrade(),
                        context.application().getRequestedAmount())
                .map(AuthorityMatrixEntry::getRequiredLevel)
                .orElseThrow(NoMatchingAuthorityMatrixEntryException::new);

        if (requiredLevel == ApprovalLevel.AUTO) {
            loanApplicationService.applyCreditPipelineOutcome(context.application().getId(), DecisionStatus.APPROVED);
            throw new ApprovalNotRequiredException("resolved authority level is AUTO");
        }

        ApprovalCase approvalCase = caseRepository.save(ApprovalCase.builder()
                .id(UUID.randomUUID())
                .decisionId(decisionId)
                .requiredLevel(requiredLevel)
                .status(ApprovalCaseStatus.PENDING_MAKER)
                .createdAt(Instant.now())
                .build());
        auditService.recordEvent(AuditEventType.APPROVAL_CASE_OPENED, "ApprovalCase", approvalCase.getId(),
                "Opened requiring " + requiredLevel + " approval for decision " + decisionId);

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

        AuthenticatedUser actor = currentActor();
        String actorUsername = actor.username();
        ApprovalLevel actorLevel = actor.approvalLevel();

        if (role == ApprovalRole.CHECKER) {
            ApprovalDecision makerDecision =
                    approvalDecisionRepository.findByApprovalCaseIdAndRole(caseId, ApprovalRole.MAKER).orElseThrow();
            if (makerDecision.getActorUsername().equalsIgnoreCase(actorUsername)) {
                throw new SelfApprovalException(actorUsername);
            }
            boolean adminOverride = actor.role() == AppRole.ADMIN;
            if (!adminOverride && (actorLevel == null || !actorLevel.atLeast(approvalCase.getRequiredLevel()))) {
                throw new InsufficientApprovalAuthorityException(actorLevel, approvalCase.getRequiredLevel());
            }
        }

        ApprovalDecision decisionRow = ApprovalDecision.builder()
                .id(UUID.randomUUID())
                .approvalCaseId(caseId)
                .role(role)
                .actorUsername(actorUsername)
                .actorLevel(actorLevel)
                .outcome(request.getOutcome())
                .comment(request.getComment())
                .decidedAt(Instant.now())
                .build();
        try {
            approvalDecisionRepository.saveAndFlush(decisionRow);
        } catch (DataIntegrityViolationException ex) {
            throw new ConcurrentApprovalConflictException(role);
        }

        ApprovalCaseStatus previousStatus = approvalCase.getStatus();
        approvalCase.setStatus(nextStatus(role, request.getOutcome()));
        caseRepository.save(approvalCase);
        auditService.recordDataChange("ApprovalCase", approvalCase.getId(), "status", previousStatus, approvalCase.getStatus());
        auditService.recordEvent(AuditEventType.APPROVAL_DECISION_RECORDED, "ApprovalCase", approvalCase.getId(),
                role + " recorded " + request.getOutcome());

        if (role == ApprovalRole.CHECKER) {
            finalizeApplicationFromCaseOutcome(approvalCase);
        }

        return toResponse(approvalCase, approvalDecisionRepository.findAllByApprovalCaseIdOrderByDecidedAtAsc(caseId));
    }

    /**
     * The checker's decision is final (Week 8's plan: "the checker's outcome is final regardless
     * of the maker's recommendation") — {@code APPROVED} or {@code REJECTED} is exactly the pair of
     * terminal states {@code recordDecision} can just have set, so this always finalizes the
     * underlying application the same way {@code openCase}'s auto-approval paths do.
     */
    private void finalizeApplicationFromCaseOutcome(ApprovalCase approvalCase) {
        CreditDecision decision = decisionRepository.findById(approvalCase.getDecisionId()).orElseThrow();
        LoanContext context = resolveContext(decision);
        DecisionStatus outcome = approvalCase.getStatus() == ApprovalCaseStatus.APPROVED
                ? DecisionStatus.APPROVED
                : DecisionStatus.DECLINED;
        loanApplicationService.applyCreditPipelineOutcome(context.application().getId(), outcome);
    }

    /**
     * The endpoint-level {@code @PreAuthorize} on {@code ApprovalCaseController} already requires
     * an authenticated {@code MAKER}/{@code CHECKER}/{@code ADMIN}, so a missing or
     * non-{@link AuthenticatedUser} principal here would mean method security was bypassed —
     * treated as a hard failure rather than defaulted.
     */
    private AuthenticatedUser currentActor() {
        return AuthenticatedUser.current()
                .orElseThrow(() -> new IllegalStateException(
                        "Approval action reached the service without an authenticated principal"));
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
