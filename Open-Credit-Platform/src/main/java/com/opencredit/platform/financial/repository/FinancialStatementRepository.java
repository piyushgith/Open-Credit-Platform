package com.opencredit.platform.financial.repository;

import com.opencredit.platform.financial.model.FinancialStatement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialStatementRepository extends JpaRepository<FinancialStatement, UUID> {

    List<FinancialStatement> findAllByApplicationId(UUID applicationId);

    Optional<FinancialStatement> findByIdAndApplicationId(UUID id, UUID applicationId);
}
