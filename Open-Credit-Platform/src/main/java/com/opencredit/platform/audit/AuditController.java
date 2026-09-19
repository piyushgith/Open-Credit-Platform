package com.opencredit.platform.audit;

import com.opencredit.platform.audit.dto.AuditBusinessEventResponse;
import com.opencredit.platform.audit.dto.AuditDataChangeResponse;
import com.opencredit.platform.audit.model.AuditBusinessEvent;
import com.opencredit.platform.audit.model.AuditDataChange;
import com.opencredit.platform.audit.repository.AuditBusinessEventRepository;
import com.opencredit.platform.audit.repository.AuditDataChangeRepository;
import com.opencredit.platform.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Read-only by construction: no method here (or anywhere else in the {@code audit} module)
 * exposes an update or delete on either table, which is what "audit records must not be casually
 * editable/deletable through normal APIs" (Week 10 plan) actually means in code.
 */
@RestController
@RequestMapping("/api/audit")
@Tag(name = "Audit", description = "Read the business-event and data-change audit trail for one entity")
public class AuditController {

    private final AuditBusinessEventRepository businessEventRepository;
    private final AuditDataChangeRepository dataChangeRepository;

    public AuditController(AuditBusinessEventRepository businessEventRepository,
                            AuditDataChangeRepository dataChangeRepository) {
        this.businessEventRepository = businessEventRepository;
        this.dataChangeRepository = dataChangeRepository;
    }

    @GetMapping("/business-events")
    @Operation(summary = "List the business-event audit trail for one entity")
    public ResponseEntity<ApiResponse<List<AuditBusinessEventResponse>>> businessEvents(
            @RequestParam String entityType, @RequestParam UUID entityId) {
        List<AuditBusinessEventResponse> response =
                businessEventRepository.findAllByEntityTypeAndEntityIdOrderByOccurredAtAsc(entityType, entityId).stream()
                        .map(this::toResponse)
                        .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/data-changes")
    @Operation(summary = "List the data-change audit trail for one entity")
    public ResponseEntity<ApiResponse<List<AuditDataChangeResponse>>> dataChanges(
            @RequestParam String entityType, @RequestParam UUID entityId) {
        List<AuditDataChangeResponse> response =
                dataChangeRepository.findAllByEntityTypeAndEntityIdOrderByChangedAtAsc(entityType, entityId).stream()
                        .map(this::toResponse)
                        .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private AuditBusinessEventResponse toResponse(AuditBusinessEvent event) {
        return AuditBusinessEventResponse.builder()
                .id(event.getId())
                .eventType(event.getEventType())
                .entityType(event.getEntityType())
                .entityId(event.getEntityId())
                .actorUsername(event.getActorUsername())
                .occurredAt(event.getOccurredAt())
                .requestId(event.getRequestId())
                .correlationId(event.getCorrelationId())
                .details(event.getDetails())
                .build();
    }

    private AuditDataChangeResponse toResponse(AuditDataChange change) {
        return AuditDataChangeResponse.builder()
                .id(change.getId())
                .entityType(change.getEntityType())
                .entityId(change.getEntityId())
                .field(change.getField())
                .oldValue(change.getOldValue())
                .newValue(change.getNewValue())
                .changedBy(change.getChangedBy())
                .changedAt(change.getChangedAt())
                .requestId(change.getRequestId())
                .correlationId(change.getCorrelationId())
                .build();
    }
}
