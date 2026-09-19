# Audit Flow

`my_docs/plan.md` §5 names this `audit-flow.png`. See
[ADR-005](../adr/ADR-005-audit-strategy.md) for the design rationale (explicit call, not a listener
or AOP) and `my_plan/week10-audit-security-observability-plan.md` for the full scoping decisions.

```mermaid
sequenceDiagram
    actor Client
    participant Filter as CorrelationIdFilter
    participant Service as owning service<br/>(e.g. ApprovalService)
    participant Audit as AuditService
    participant DB as PostgreSQL

    Client->>Filter: HTTP request (+ optional X-Request-Id/X-Correlation-Id)
    Filter->>Filter: generate/truncate ids, put in RequestContext + MDC
    Filter->>Service: forward request

    Note over Service: same @Transactional method that<br/>persists the business state change
    Service->>DB: persist entity mutation
    Service->>Audit: recordDataChange(entity, field, old, new)
    Audit->>DB: INSERT audit_data_change
    Service->>Audit: recordEvent(eventType, entity, details)
    Audit->>DB: INSERT audit_business_event

    Note over Service,DB: one transaction: if either audit insert fails,<br/>the business mutation rolls back too (fail closed)
    Service-->>Client: response
```

Both audit tables are append-only by construction (`AuditController` exposes only `@GetMapping`s);
`actor`/`requestId`/`correlationId` are sourced from `SecurityContextHolder`/`RequestContext`, never
passed by the caller.
