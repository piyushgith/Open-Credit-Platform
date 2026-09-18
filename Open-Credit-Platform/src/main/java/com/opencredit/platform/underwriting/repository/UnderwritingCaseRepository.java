package com.opencredit.platform.underwriting.repository;

import com.opencredit.platform.underwriting.model.UnderwritingCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UnderwritingCaseRepository extends JpaRepository<UnderwritingCase, UUID> {

    Optional<UnderwritingCase> findByApplicationId(UUID applicationId);
}
