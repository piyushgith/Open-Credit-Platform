package com.opencredit.platform.disbursement.repository;

import com.opencredit.platform.disbursement.model.DisbursementTranche;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DisbursementTrancheRepository extends JpaRepository<DisbursementTranche, UUID> {

    List<DisbursementTranche> findAllByDisbursementIdOrderByTrancheNumberAsc(UUID disbursementId);

    long countByDisbursementId(UUID disbursementId);
}
