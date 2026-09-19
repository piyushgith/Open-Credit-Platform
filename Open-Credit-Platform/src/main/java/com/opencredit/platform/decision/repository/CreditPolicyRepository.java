package com.opencredit.platform.decision.repository;

import com.opencredit.platform.decision.model.CreditPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditPolicyRepository extends JpaRepository<CreditPolicy, UUID> {

    Optional<CreditPolicy> findByActiveTrue();

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    List<CreditPolicy> findAllByOrderByCreatedAtAsc();
}
