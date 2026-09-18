--liquibase formatted sql

--changeset piyushprasad:006-create-kyc-case
CREATE TABLE kyc_case (
    id             UUID           PRIMARY KEY,
    application_id UUID           NOT NULL UNIQUE REFERENCES loan_application (id),
    status         VARCHAR(20)    NOT NULL,
    remarks        VARCHAR(500),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version        BIGINT         NOT NULL DEFAULT 0
);
--rollback DROP TABLE kyc_case;
