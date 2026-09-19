package com.opencredit.platform.decision.repository;

import com.opencredit.platform.decision.model.RuleResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RuleResultRepository extends JpaRepository<RuleResult, UUID> {

    List<RuleResult> findAllByDecisionId(UUID decisionId);
}
