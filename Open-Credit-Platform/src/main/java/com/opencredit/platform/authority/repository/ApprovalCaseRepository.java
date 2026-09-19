package com.opencredit.platform.authority.repository;

import com.opencredit.platform.authority.model.ApprovalCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApprovalCaseRepository extends JpaRepository<ApprovalCase, UUID> {

    Optional<ApprovalCase> findByDecisionId(UUID decisionId);

    boolean existsByDecisionId(UUID decisionId);
}
