package com.opencredit.platform.decision.repository;

import com.opencredit.platform.decision.model.CreditRule;
import com.opencredit.platform.decision.model.CreditRuleFactorCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditRuleRepository extends JpaRepository<CreditRule, UUID> {

    List<CreditRule> findAllByPolicyIdOrderByRuleOrderAsc(UUID policyId);

    Optional<CreditRule> findByPolicyIdAndFactorCode(UUID policyId, CreditRuleFactorCode factorCode);
}
