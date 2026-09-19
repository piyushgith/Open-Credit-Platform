package com.opencredit.platform.financial.repository;

import com.opencredit.platform.financial.model.FinancialRatio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FinancialRatioRepository extends JpaRepository<FinancialRatio, UUID> {

    List<FinancialRatio> findAllByAnalysisRunId(UUID analysisRunId);
}
