package com.opencredit.platform.decision.repository;

import com.opencredit.platform.decision.model.CreditDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CreditDecisionRepository extends JpaRepository<CreditDecision, UUID> {

    List<CreditDecision> findAllByScoreId(UUID scoreId);

    boolean existsByScoreIdAndPolicyId(UUID scoreId, UUID policyId);

    boolean existsByPolicyId(UUID policyId);
}
