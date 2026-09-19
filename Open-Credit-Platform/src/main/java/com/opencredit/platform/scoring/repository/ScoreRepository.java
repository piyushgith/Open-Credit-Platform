package com.opencredit.platform.scoring.repository;

import com.opencredit.platform.scoring.model.Score;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScoreRepository extends JpaRepository<Score, UUID> {

    List<Score> findAllByAnalysisRunId(UUID analysisRunId);

    boolean existsByAnalysisRunIdAndScorecardId(UUID analysisRunId, UUID scorecardId);

    boolean existsByScorecardId(UUID scorecardId);
}
