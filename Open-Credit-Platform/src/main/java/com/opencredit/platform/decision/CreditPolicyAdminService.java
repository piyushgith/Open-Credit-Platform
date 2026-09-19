package com.opencredit.platform.decision;

import com.opencredit.platform.decision.dto.CreditPolicyRequest;
import com.opencredit.platform.decision.dto.CreditPolicyResponse;
import com.opencredit.platform.decision.dto.CreditRuleRequest;
import com.opencredit.platform.decision.dto.CreditRuleResponse;
import com.opencredit.platform.decision.exception.CreditPolicyInUseException;
import com.opencredit.platform.decision.exception.CreditPolicyNotFoundException;
import com.opencredit.platform.decision.exception.CreditRuleNotFoundException;
import com.opencredit.platform.decision.exception.DuplicateCreditPolicyNameException;
import com.opencredit.platform.decision.exception.DuplicateCreditRuleFactorException;
import com.opencredit.platform.decision.model.CreditPolicy;
import com.opencredit.platform.decision.model.CreditRule;
import com.opencredit.platform.decision.repository.CreditDecisionRepository;
import com.opencredit.platform.decision.repository.CreditPolicyRepository;
import com.opencredit.platform.decision.repository.CreditRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CRUD for {@link CreditPolicy}/{@link CreditRule} configuration — kept separate from
 * {@link DecisionService}, which only decides scores against whichever policy is active.
 */
@Service
@Transactional
public class CreditPolicyAdminService {

    private final CreditPolicyRepository policyRepository;
    private final CreditRuleRepository ruleRepository;
    private final CreditDecisionRepository decisionRepository;

    public CreditPolicyAdminService(CreditPolicyRepository policyRepository, CreditRuleRepository ruleRepository,
                                     CreditDecisionRepository decisionRepository) {
        this.policyRepository = policyRepository;
        this.ruleRepository = ruleRepository;
        this.decisionRepository = decisionRepository;
    }

    public CreditPolicyResponse create(CreditPolicyRequest request) {
        if (policyRepository.existsByName(request.getName())) {
            throw new DuplicateCreditPolicyNameException(request.getName());
        }

        CreditPolicy policy = policyRepository.save(CreditPolicy.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .active(false)
                .createdAt(Instant.now())
                .build());

        return toResponse(policy, List.of());
    }

    @Transactional(readOnly = true)
    public List<CreditPolicyResponse> list() {
        return policyRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(policy -> toResponse(policy, ruleRepository.findAllByPolicyIdOrderByRuleOrderAsc(policy.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CreditPolicyResponse get(UUID policyId) {
        CreditPolicy policy = getEntity(policyId);
        return toResponse(policy, ruleRepository.findAllByPolicyIdOrderByRuleOrderAsc(policyId));
    }

    public CreditPolicyResponse update(UUID policyId, CreditPolicyRequest request) {
        CreditPolicy policy = getEntity(policyId);
        if (!policy.getName().equals(request.getName())
                && policyRepository.existsByNameAndIdNot(request.getName(), policyId)) {
            throw new DuplicateCreditPolicyNameException(request.getName());
        }

        policy.setName(request.getName());
        policyRepository.save(policy);

        return toResponse(policy, ruleRepository.findAllByPolicyIdOrderByRuleOrderAsc(policyId));
    }

    public void delete(UUID policyId) {
        CreditPolicy policy = getEntity(policyId);
        if (policy.isActive()) {
            throw new CreditPolicyInUseException(policyId, "it is the active credit policy");
        }
        if (decisionRepository.existsByPolicyId(policyId)) {
            throw new CreditPolicyInUseException(policyId, "it has already been used to decide a score");
        }
        ruleRepository.deleteAll(ruleRepository.findAllByPolicyIdOrderByRuleOrderAsc(policyId));
        policyRepository.delete(policy);
    }

    /**
     * Deactivates the currently-active policy (if any) before activating this one, so the
     * {@code uq_credit_policy_active} partial unique index never sees two active rows at once —
     * same two-step flow {@code ScorecardAdminService} uses to swap the active scorecard.
     */
    public CreditPolicyResponse activate(UUID policyId) {
        CreditPolicy policy = getEntity(policyId);
        policyRepository.findByActiveTrue().ifPresent(current -> {
            if (!current.getId().equals(policyId)) {
                current.setActive(false);
                policyRepository.saveAndFlush(current);
            }
        });
        policy.setActive(true);
        policyRepository.save(policy);
        return toResponse(policy, ruleRepository.findAllByPolicyIdOrderByRuleOrderAsc(policyId));
    }

    public CreditRuleResponse addRule(UUID policyId, CreditRuleRequest request) {
        getEntity(policyId);
        if (ruleRepository.findByPolicyIdAndFactorCode(policyId, request.getFactorCode()).isPresent()) {
            throw new DuplicateCreditRuleFactorException(request.getFactorCode());
        }

        CreditRule rule = ruleRepository.save(CreditRule.builder()
                .id(UUID.randomUUID())
                .policyId(policyId)
                .factorCode(request.getFactorCode())
                .operator(request.getOperator())
                .thresholdValue(request.getThresholdValue())
                .severity(request.getSeverity())
                .ruleOrder(request.getRuleOrder())
                .description(request.getDescription())
                .createdAt(Instant.now())
                .build());

        return toRuleResponse(rule);
    }

    public CreditRuleResponse updateRule(UUID policyId, UUID ruleId, CreditRuleRequest request) {
        getEntity(policyId);
        CreditRule rule = getRuleEntity(policyId, ruleId);

        ruleRepository.findByPolicyIdAndFactorCode(policyId, request.getFactorCode())
                .filter(existing -> !existing.getId().equals(ruleId))
                .ifPresent(existing -> {
                    throw new DuplicateCreditRuleFactorException(request.getFactorCode());
                });

        rule.setFactorCode(request.getFactorCode());
        rule.setOperator(request.getOperator());
        rule.setThresholdValue(request.getThresholdValue());
        rule.setSeverity(request.getSeverity());
        rule.setRuleOrder(request.getRuleOrder());
        rule.setDescription(request.getDescription());
        ruleRepository.save(rule);

        return toRuleResponse(rule);
    }

    public void deleteRule(UUID policyId, UUID ruleId) {
        getEntity(policyId);
        CreditRule rule = getRuleEntity(policyId, ruleId);
        ruleRepository.delete(rule);
    }

    private CreditRule getRuleEntity(UUID policyId, UUID ruleId) {
        return ruleRepository.findById(ruleId)
                .filter(rule -> rule.getPolicyId().equals(policyId))
                .orElseThrow(() -> new CreditRuleNotFoundException(ruleId));
    }

    private CreditPolicy getEntity(UUID policyId) {
        return policyRepository.findById(policyId)
                .orElseThrow(() -> new CreditPolicyNotFoundException(policyId));
    }

    private CreditPolicyResponse toResponse(CreditPolicy policy, List<CreditRule> rules) {
        return CreditPolicyResponse.builder()
                .id(policy.getId())
                .name(policy.getName())
                .active(policy.isActive())
                .createdAt(policy.getCreatedAt())
                .rules(rules.stream().map(this::toRuleResponse).toList())
                .build();
    }

    private CreditRuleResponse toRuleResponse(CreditRule rule) {
        return CreditRuleResponse.builder()
                .id(rule.getId())
                .factorCode(rule.getFactorCode())
                .operator(rule.getOperator())
                .thresholdValue(rule.getThresholdValue())
                .severity(rule.getSeverity())
                .ruleOrder(rule.getRuleOrder())
                .description(rule.getDescription())
                .build();
    }
}
