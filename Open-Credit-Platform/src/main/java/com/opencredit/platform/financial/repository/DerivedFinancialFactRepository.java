package com.opencredit.platform.financial.repository;

import com.opencredit.platform.financial.model.DerivedFinancialFact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DerivedFinancialFactRepository extends JpaRepository<DerivedFinancialFact, UUID> {

    List<DerivedFinancialFact> findAllByAnalysisRunId(UUID analysisRunId);
}
