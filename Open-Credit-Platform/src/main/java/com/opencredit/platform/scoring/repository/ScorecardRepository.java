package com.opencredit.platform.scoring.repository;

import com.opencredit.platform.scoring.model.Scorecard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScorecardRepository extends JpaRepository<Scorecard, UUID> {

    Optional<Scorecard> findByActiveTrue();

    boolean existsByNameAndIdNot(String name, UUID id);

    boolean existsByName(String name);

    List<Scorecard> findAllByOrderByCreatedAtAsc();
}
