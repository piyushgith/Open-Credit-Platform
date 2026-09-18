package com.opencredit.platform.financial.repository;

import com.opencredit.platform.financial.model.FinancialPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FinancialPeriodRepository extends JpaRepository<FinancialPeriod, UUID> {
}
