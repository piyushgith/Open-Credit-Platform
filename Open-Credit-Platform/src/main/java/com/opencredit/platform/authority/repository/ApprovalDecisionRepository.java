package com.opencredit.platform.authority.repository;

import com.opencredit.platform.authority.model.ApprovalDecision;
import com.opencredit.platform.authority.model.ApprovalRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalDecisionRepository extends JpaRepository<ApprovalDecision, UUID> {

    List<ApprovalDecision> findAllByApprovalCaseIdOrderByDecidedAtAsc(UUID approvalCaseId);

    Optional<ApprovalDecision> findByApprovalCaseIdAndRole(UUID approvalCaseId, ApprovalRole role);
}
