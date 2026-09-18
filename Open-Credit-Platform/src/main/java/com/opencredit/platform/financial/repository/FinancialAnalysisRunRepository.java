package com.opencredit.platform.financial.repository;

import com.opencredit.platform.financial.model.FinancialAnalysisRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FinancialAnalysisRunRepository extends JpaRepository<FinancialAnalysisRun, UUID> {

    List<FinancialAnalysisRun> findAllByStatementIdOrderByRunAtAsc(UUID statementId);
}
