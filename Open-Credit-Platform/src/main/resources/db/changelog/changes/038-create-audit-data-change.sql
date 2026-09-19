--liquibase formatted sql

--changeset piyushprasad:038-create-audit-data-change
CREATE TABLE audit_data_change (
    id              UUID           PRIMARY KEY,
    entity_type     VARCHAR(60)    NOT NULL,
    entity_id       UUID           NOT NULL,
    field           VARCHAR(60)    NOT NULL,
    old_value       VARCHAR(200),
    new_value       VARCHAR(200),
    changed_by      VARCHAR(60)    NOT NULL,
    changed_at      TIMESTAMPTZ    NOT NULL,
    request_id      VARCHAR(60)    NOT NULL,
    correlation_id  VARCHAR(60)    NOT NULL
);

CREATE INDEX idx_audit_data_change_entity ON audit_data_change (entity_type, entity_id);
--rollback DROP TABLE audit_data_change;
