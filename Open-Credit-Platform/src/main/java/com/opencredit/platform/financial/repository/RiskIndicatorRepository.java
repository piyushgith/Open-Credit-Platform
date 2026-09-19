package com.opencredit.platform.financial.repository;

import com.opencredit.platform.financial.model.RiskIndicator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RiskIndicatorRepository extends JpaRepository<RiskIndicator, UUID> {

    List<RiskIndicator> findAllByAnalysisRunId(UUID analysisRunId);
}
