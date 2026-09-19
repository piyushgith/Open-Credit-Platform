package com.opencredit.platform.offer.repository;

import com.opencredit.platform.offer.model.Sanction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SanctionRepository extends JpaRepository<Sanction, UUID> {

    Optional<Sanction> findByApplicationId(UUID applicationId);

    boolean existsByApplicationId(UUID applicationId);
}
