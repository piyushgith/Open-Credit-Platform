package com.opencredit.platform.scoring.repository;

import com.opencredit.platform.scoring.model.RiskFactor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RiskFactorRepository extends JpaRepository<RiskFactor, UUID> {

    List<RiskFactor> findAllByScoreId(UUID scoreId);
}
