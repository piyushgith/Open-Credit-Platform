--liquibase formatted sql

--changeset piyushprasad:029-create-approval-case
CREATE TABLE approval_case (
    id                UUID           PRIMARY KEY,
    decision_id       UUID           NOT NULL UNIQUE REFERENCES credit_decision (id),
    required_level    VARCHAR(32)    NOT NULL,
    status            VARCHAR(32)    NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version           BIGINT         NOT NULL DEFAULT 0
);
--rollback DROP TABLE approval_case;
