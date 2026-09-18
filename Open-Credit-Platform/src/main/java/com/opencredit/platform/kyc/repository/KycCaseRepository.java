package com.opencredit.platform.kyc.repository;

import com.opencredit.platform.kyc.model.KycCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KycCaseRepository extends JpaRepository<KycCase, UUID> {

    Optional<KycCase> findByApplicationId(UUID applicationId);

    boolean existsByApplicationId(UUID applicationId);
}
