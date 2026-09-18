--liquibase formatted sql

--changeset piyushprasad:001-create-loan-application
CREATE SEQUENCE loan_reference_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE loan_application (
    id                 UUID           PRIMARY KEY,
    reference_number   VARCHAR(32)    NOT NULL,
    product_type       VARCHAR(32)    NOT NULL,
    applicant_name     VARCHAR(160)   NOT NULL,
    requested_amount   NUMERIC(19,2)  NOT NULL,
    tenure_months      INTEGER        NOT NULL,
    status             VARCHAR(32)    NOT NULL,
    decision           VARCHAR(32)    NOT NULL,
    request_details    JSONB          NOT NULL,
    decision_details   JSONB          NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version            BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_loan_application_reference_number UNIQUE (reference_number),
    CONSTRAINT chk_loan_application_requested_amount CHECK (requested_amount > 0),
    CONSTRAINT chk_loan_application_tenure_months CHECK (tenure_months BETWEEN 1 AND 360)
);

CREATE INDEX idx_loan_application_product_type ON loan_application (product_type);
CREATE INDEX idx_loan_application_created_at ON loan_application (created_at DESC);
--rollback DROP TABLE loan_application; DROP SEQUENCE loan_reference_seq;
