package com.opencredit.platform.audit.repository;

import com.opencredit.platform.audit.model.AuditBusinessEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditBusinessEventRepository extends JpaRepository<AuditBusinessEvent, UUID> {

    List<AuditBusinessEvent> findAllByEntityTypeAndEntityIdOrderByOccurredAtAsc(String entityType, UUID entityId);
}
