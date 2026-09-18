package com.opencredit.platform.financial.repository;

import com.opencredit.platform.financial.model.FinancialLineItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FinancialLineItemRepository extends JpaRepository<FinancialLineItem, UUID> {

    List<FinancialLineItem> findAllByStatementId(UUID statementId);
}
