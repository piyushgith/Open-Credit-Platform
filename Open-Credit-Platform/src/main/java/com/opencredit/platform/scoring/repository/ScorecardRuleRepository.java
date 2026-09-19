package com.opencredit.platform.scoring.repository;

import com.opencredit.platform.scoring.model.ScoreFactorCode;
import com.opencredit.platform.scoring.model.ScorecardRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScorecardRuleRepository extends JpaRepository<ScorecardRule, UUID> {

    List<ScorecardRule> findAllByScorecardIdOrderByFactorCodeAscBandOrderAsc(UUID scorecardId);

    List<ScorecardRule> findAllByScorecardIdAndFactorCode(UUID scorecardId, ScoreFactorCode factorCode);
}
