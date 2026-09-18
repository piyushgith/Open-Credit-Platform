package com.opencredit.platform.underwriting.repository;

import com.opencredit.platform.underwriting.model.UnderwritingAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnderwritingAttemptRepository extends JpaRepository<UnderwritingAttempt, UUID> {

    Optional<UnderwritingAttempt> findByUnderwritingCaseIdAndActiveTrue(UUID underwritingCaseId);

    long countByUnderwritingCaseId(UUID underwritingCaseId);

    List<UnderwritingAttempt> findAllByUnderwritingCaseIdOrderByCycleNumberAsc(UUID underwritingCaseId);
}
