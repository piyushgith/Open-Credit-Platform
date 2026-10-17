package com.opencredit.platform.authority.repository;

import com.opencredit.platform.authority.dto.ApprovalCaseSummaryResponse;
import com.opencredit.platform.authority.model.ApprovalCase;
import com.opencredit.platform.authority.model.ApprovalCaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ApprovalCaseRepository extends JpaRepository<ApprovalCase, UUID> {

    Optional<ApprovalCase> findByDecisionId(UUID decisionId);

    boolean existsByDecisionId(UUID decisionId);

    /**
     * Queue rows: each case joined (by id, there are no entity associations across modules) to
     * the application it ultimately belongs to: case -> decision -> score -> analysis run ->
     * statement -> application. Oldest first, so the longest-waiting case is at the top.
     */
    @Query(value = QUEUE_SELECT + " order by c.createdAt asc, c.id asc",
            countQuery = "select count(c) from ApprovalCase c")
    Page<ApprovalCaseSummaryResponse> findQueue(Pageable pageable);

    @Query(value = QUEUE_SELECT + " where c.status = :status order by c.createdAt asc, c.id asc",
            countQuery = "select count(c) from ApprovalCase c where c.status = :status")
    Page<ApprovalCaseSummaryResponse> findQueueByStatus(@Param("status") ApprovalCaseStatus status, Pageable pageable);

    String QUEUE_SELECT = """
            select new com.opencredit.platform.authority.dto.ApprovalCaseSummaryResponse(
                c.id, c.decisionId, a.referenceNumber, a.applicantName, a.productType,
                a.requestedAmount, c.requiredLevel, c.status, c.createdAt)
            from ApprovalCase c
            join CreditDecision d on d.id = c.decisionId
            join Score s on s.id = d.scoreId
            join FinancialAnalysisRun r on r.id = s.analysisRunId
            join FinancialStatement fs on fs.id = r.statementId
            join LoanApplication a on a.id = fs.applicationId""";
}
