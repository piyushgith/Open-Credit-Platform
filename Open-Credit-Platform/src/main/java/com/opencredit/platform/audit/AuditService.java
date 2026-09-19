package com.opencredit.platform.audit;

import com.opencredit.platform.audit.model.AuditBusinessEvent;
import com.opencredit.platform.audit.model.AuditDataChange;
import com.opencredit.platform.audit.model.AuditEventType;
import com.opencredit.platform.audit.repository.AuditBusinessEventRepository;
import com.opencredit.platform.audit.repository.AuditDataChangeRepository;
import com.opencredit.platform.common.web.RequestContext;
import com.opencredit.platform.security.support.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Called directly by the owning service at the exact line it already persists its state change —
 * see the Week 10 plan's "Context" section for why this is a plain method call and not a listener.
 * Both methods here run inside the caller's own {@code @Transactional} method (default
 * {@code REQUIRED} propagation): the audit row and the business mutation commit or roll back
 * together, so an audit write failure fails the business action too, rather than silently losing
 * the trail.
 */
@Service
@Transactional
public class AuditService {

    private final AuditBusinessEventRepository businessEventRepository;
    private final AuditDataChangeRepository dataChangeRepository;

    public AuditService(AuditBusinessEventRepository businessEventRepository,
                         AuditDataChangeRepository dataChangeRepository) {
        this.businessEventRepository = businessEventRepository;
        this.dataChangeRepository = dataChangeRepository;
    }

    public void recordEvent(AuditEventType eventType, String entityType, UUID entityId, String details) {
        businessEventRepository.save(AuditBusinessEvent.builder()
                .id(UUID.randomUUID())
                .eventType(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .actorUsername(currentActor())
                .occurredAt(Instant.now())
                .requestId(RequestContext.currentRequestId())
                .correlationId(RequestContext.currentCorrelationId())
                .details(details)
                .build());
    }

    public void recordDataChange(String entityType, UUID entityId, String field, Object oldValue, Object newValue) {
        dataChangeRepository.save(AuditDataChange.builder()
                .id(UUID.randomUUID())
                .entityType(entityType)
                .entityId(entityId)
                .field(field)
                .oldValue(oldValue != null ? oldValue.toString() : null)
                .newValue(newValue != null ? newValue.toString() : null)
                .changedBy(currentActor())
                .changedAt(Instant.now())
                .requestId(RequestContext.currentRequestId())
                .correlationId(RequestContext.currentCorrelationId())
                .build());
    }

    private String currentActor() {
        return AuthenticatedUser.current().map(AuthenticatedUser::username).orElse("system");
    }
}
