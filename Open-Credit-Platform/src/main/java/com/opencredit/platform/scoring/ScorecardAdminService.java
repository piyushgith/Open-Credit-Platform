package com.opencredit.platform.scoring;

import com.opencredit.platform.scoring.dto.ScorecardRequest;
import com.opencredit.platform.scoring.dto.ScorecardResponse;
import com.opencredit.platform.scoring.dto.ScorecardRuleRequest;
import com.opencredit.platform.scoring.dto.ScorecardRuleResponse;
import com.opencredit.platform.scoring.exception.DuplicateScorecardNameException;
import com.opencredit.platform.scoring.exception.InvalidScorecardRangeException;
import com.opencredit.platform.scoring.exception.InvalidScorecardRuleRangeException;
import com.opencredit.platform.scoring.exception.OverlappingScorecardRuleException;
import com.opencredit.platform.scoring.exception.ScorecardInUseException;
import com.opencredit.platform.scoring.exception.ScorecardNotFoundException;
import com.opencredit.platform.scoring.exception.ScorecardRuleNotFoundException;
import com.opencredit.platform.scoring.model.ScoreFactorCode;
import com.opencredit.platform.scoring.model.Scorecard;
import com.opencredit.platform.scoring.model.ScorecardRule;
import com.opencredit.platform.scoring.repository.ScoreRepository;
import com.opencredit.platform.scoring.repository.ScorecardRepository;
import com.opencredit.platform.scoring.repository.ScorecardRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CRUD for {@link Scorecard}/{@link ScorecardRule} configuration — kept separate from
 * {@link ScoreService}, which only computes scores against whichever scorecard is active.
 */
@Service
@Transactional
public class ScorecardAdminService {

    private final ScorecardRepository scorecardRepository;
    private final ScorecardRuleRepository ruleRepository;
    private final ScoreRepository scoreRepository;

    public ScorecardAdminService(ScorecardRepository scorecardRepository, ScorecardRuleRepository ruleRepository,
                                  ScoreRepository scoreRepository) {
        this.scorecardRepository = scorecardRepository;
        this.ruleRepository = ruleRepository;
        this.scoreRepository = scoreRepository;
    }

    public ScorecardResponse create(ScorecardRequest request) {
        assertValidRange(request.getMinScore(), request.getBaseScore(), request.getMaxScore());
        if (scorecardRepository.existsByName(request.getName())) {
            throw new DuplicateScorecardNameException(request.getName());
        }

        Scorecard scorecard = scorecardRepository.save(Scorecard.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .active(false)
                .baseScore(request.getBaseScore())
                .minScore(request.getMinScore())
                .maxScore(request.getMaxScore())
                .createdAt(Instant.now())
                .build());

        return toResponse(scorecard, List.of());
    }

    @Transactional(readOnly = true)
    public List<ScorecardResponse> list() {
        return scorecardRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(scorecard -> toResponse(scorecard, ruleRepository
                        .findAllByScorecardIdOrderByFactorCodeAscBandOrderAsc(scorecard.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ScorecardResponse get(UUID scorecardId) {
        Scorecard scorecard = getEntity(scorecardId);
        return toResponse(scorecard, ruleRepository.findAllByScorecardIdOrderByFactorCodeAscBandOrderAsc(scorecardId));
    }

    public ScorecardResponse update(UUID scorecardId, ScorecardRequest request) {
        assertValidRange(request.getMinScore(), request.getBaseScore(), request.getMaxScore());
        Scorecard scorecard = getEntity(scorecardId);
        if (!scorecard.getName().equals(request.getName())
                && scorecardRepository.existsByNameAndIdNot(request.getName(), scorecardId)) {
            throw new DuplicateScorecardNameException(request.getName());
        }

        scorecard.setName(request.getName());
        scorecard.setBaseScore(request.getBaseScore());
        scorecard.setMinScore(request.getMinScore());
        scorecard.setMaxScore(request.getMaxScore());
        scorecardRepository.save(scorecard);

        return toResponse(scorecard, ruleRepository.findAllByScorecardIdOrderByFactorCodeAscBandOrderAsc(scorecardId));
    }

    public void delete(UUID scorecardId) {
        Scorecard scorecard = getEntity(scorecardId);
        if (scorecard.isActive()) {
            throw new ScorecardInUseException(scorecardId, "it is the active scorecard");
        }
        if (scoreRepository.existsByScorecardId(scorecardId)) {
            throw new ScorecardInUseException(scorecardId, "it has already been used to score an analysis run");
        }
        ruleRepository.deleteAll(ruleRepository.findAllByScorecardIdOrderByFactorCodeAscBandOrderAsc(scorecardId));
        scorecardRepository.delete(scorecard);
    }

    /**
     * Deactivates the currently-active scorecard (if any) before activating this one, so the
     * {@code uq_scorecard_active} partial unique index never sees two active rows at once —
     * same two-step flow the underwriting module uses to swap the active attempt.
     */
    public ScorecardResponse activate(UUID scorecardId) {
        Scorecard scorecard = getEntity(scorecardId);
        scorecardRepository.findByActiveTrue().ifPresent(current -> {
            if (!current.getId().equals(scorecardId)) {
                current.setActive(false);
                scorecardRepository.saveAndFlush(current);
            }
        });
        scorecard.setActive(true);
        scorecardRepository.save(scorecard);
        return toResponse(scorecard, ruleRepository.findAllByScorecardIdOrderByFactorCodeAscBandOrderAsc(scorecardId));
    }

    public ScorecardRuleResponse addRule(UUID scorecardId, ScorecardRuleRequest request) {
        getEntity(scorecardId);
        assertValidRuleRange(request.getMinValue(), request.getMaxValue());
        assertNoOverlap(scorecardId, request.getFactorCode(), request.getMinValue(), request.getMaxValue(), null);

        ScorecardRule rule = ruleRepository.save(ScorecardRule.builder()
                .id(UUID.randomUUID())
                .scorecardId(scorecardId)
                .factorCode(request.getFactorCode())
                .bandOrder(request.getBandOrder())
                .minValue(request.getMinValue())
                .maxValue(request.getMaxValue())
                .points(request.getPoints())
                .createdAt(Instant.now())
                .build());

        return toRuleResponse(rule);
    }

    public ScorecardRuleResponse updateRule(UUID scorecardId, UUID ruleId, ScorecardRuleRequest request) {
        getEntity(scorecardId);
        ScorecardRule rule = ruleRepository.findById(ruleId)
                .filter(r -> r.getScorecardId().equals(scorecardId))
                .orElseThrow(() -> new ScorecardRuleNotFoundException(ruleId));
        assertValidRuleRange(request.getMinValue(), request.getMaxValue());
        assertNoOverlap(scorecardId, request.getFactorCode(), request.getMinValue(), request.getMaxValue(), ruleId);

        rule.setFactorCode(request.getFactorCode());
        rule.setBandOrder(request.getBandOrder());
        rule.setMinValue(request.getMinValue());
        rule.setMaxValue(request.getMaxValue());
        rule.setPoints(request.getPoints());
        ruleRepository.save(rule);

        return toRuleResponse(rule);
    }

    public void deleteRule(UUID scorecardId, UUID ruleId) {
        getEntity(scorecardId);
        ScorecardRule rule = ruleRepository.findById(ruleId)
                .filter(r -> r.getScorecardId().equals(scorecardId))
                .orElseThrow(() -> new ScorecardRuleNotFoundException(ruleId));
        ruleRepository.delete(rule);
    }

    private void assertNoOverlap(UUID scorecardId, ScoreFactorCode factorCode, BigDecimal minValue, BigDecimal maxValue,
                                  UUID excludingRuleId) {
        boolean overlaps = ruleRepository.findAllByScorecardIdAndFactorCode(scorecardId, factorCode).stream()
                .filter(existing -> excludingRuleId == null || !existing.getId().equals(excludingRuleId))
                .anyMatch(existing -> rangesOverlap(minValue, maxValue, existing.getMinValue(), existing.getMaxValue()));
        if (overlaps) {
            throw new OverlappingScorecardRuleException(factorCode);
        }
    }

    private static boolean rangesOverlap(BigDecimal aMin, BigDecimal aMax, BigDecimal bMin, BigDecimal bMax) {
        return lowerLessThanUpper(aMin, bMax) && lowerLessThanUpper(bMin, aMax);
    }

    /** {@code lower < upper}, treating a {@code null} lower bound as -infinity and a {@code null} upper bound as +infinity. */
    private static boolean lowerLessThanUpper(BigDecimal lower, BigDecimal upper) {
        if (lower == null || upper == null) {
            return true;
        }
        return lower.compareTo(upper) < 0;
    }

    private static void assertValidRuleRange(BigDecimal minValue, BigDecimal maxValue) {
        if (minValue != null && maxValue != null && minValue.compareTo(maxValue) >= 0) {
            throw new InvalidScorecardRuleRangeException(minValue, maxValue);
        }
    }

    private static void assertValidRange(int minScore, int baseScore, int maxScore) {
        if (minScore > baseScore || baseScore > maxScore) {
            throw new InvalidScorecardRangeException(minScore, baseScore, maxScore);
        }
    }

    private Scorecard getEntity(UUID scorecardId) {
        return scorecardRepository.findById(scorecardId)
                .orElseThrow(() -> new ScorecardNotFoundException(scorecardId));
    }

    private ScorecardResponse toResponse(Scorecard scorecard, List<ScorecardRule> rules) {
        return ScorecardResponse.builder()
                .id(scorecard.getId())
                .name(scorecard.getName())
                .active(scorecard.isActive())
                .baseScore(scorecard.getBaseScore())
                .minScore(scorecard.getMinScore())
                .maxScore(scorecard.getMaxScore())
                .createdAt(scorecard.getCreatedAt())
                .rules(rules.stream().map(this::toRuleResponse).toList())
                .build();
    }

    private ScorecardRuleResponse toRuleResponse(ScorecardRule rule) {
        return ScorecardRuleResponse.builder()
                .id(rule.getId())
                .factorCode(rule.getFactorCode())
                .bandOrder(rule.getBandOrder())
                .minValue(rule.getMinValue())
                .maxValue(rule.getMaxValue())
                .points(rule.getPoints())
                .build();
    }
}
