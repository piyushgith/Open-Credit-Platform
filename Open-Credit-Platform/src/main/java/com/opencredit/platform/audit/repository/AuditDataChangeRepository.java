package com.opencredit.platform.audit.repository;

import com.opencredit.platform.audit.model.AuditDataChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditDataChangeRepository extends JpaRepository<AuditDataChange, UUID> {

    List<AuditDataChange> findAllByEntityTypeAndEntityIdOrderByChangedAtAsc(String entityType, UUID entityId);
}
