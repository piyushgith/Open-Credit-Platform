--liquibase formatted sql

--changeset piyushprasad:008-create-underwriting-case
CREATE TABLE underwriting_case (
    id             UUID           PRIMARY KEY,
    application_id UUID           NOT NULL UNIQUE REFERENCES loan_application (id),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version        BIGINT         NOT NULL DEFAULT 0
);
--rollback DROP TABLE underwriting_case;
