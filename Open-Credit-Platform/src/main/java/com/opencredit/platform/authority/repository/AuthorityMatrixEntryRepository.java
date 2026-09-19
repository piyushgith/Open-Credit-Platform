package com.opencredit.platform.authority.repository;

import com.opencredit.platform.authority.model.AuthorityMatrixEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuthorityMatrixEntryRepository extends JpaRepository<AuthorityMatrixEntry, UUID> {

    List<AuthorityMatrixEntry> findAllByOrderByMatchOrderAsc();

    List<AuthorityMatrixEntry> findAllByActiveTrueOrderByMatchOrderAsc();

    boolean existsByActiveTrueAndMatchOrder(int matchOrder);

    boolean existsByActiveTrueAndMatchOrderAndIdNot(int matchOrder, UUID id);
}
