--liquibase formatted sql

--changeset piyushprasad:037-create-audit-business-event
CREATE TABLE audit_business_event (
    id              UUID           PRIMARY KEY,
    event_type      VARCHAR(32)    NOT NULL,
    entity_type     VARCHAR(60)    NOT NULL,
    entity_id       UUID           NOT NULL,
    actor_username  VARCHAR(60)    NOT NULL,
    occurred_at     TIMESTAMPTZ    NOT NULL,
    request_id      VARCHAR(60)    NOT NULL,
    correlation_id  VARCHAR(60)    NOT NULL,
    details         VARCHAR(500)
);

-- The access pattern this table exists for: "show me every business event for entity X."
CREATE INDEX idx_audit_business_event_entity ON audit_business_event (entity_type, entity_id);
--rollback DROP TABLE audit_business_event;
