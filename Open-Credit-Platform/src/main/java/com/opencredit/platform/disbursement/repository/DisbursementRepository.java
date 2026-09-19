package com.opencredit.platform.disbursement.repository;

import com.opencredit.platform.disbursement.model.Disbursement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DisbursementRepository extends JpaRepository<Disbursement, UUID> {

    Optional<Disbursement> findBySanctionId(UUID sanctionId);
}
