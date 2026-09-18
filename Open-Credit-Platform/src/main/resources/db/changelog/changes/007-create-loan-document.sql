--liquibase formatted sql

--changeset piyushprasad:007-create-loan-document
CREATE TABLE loan_document (
    id                 UUID           PRIMARY KEY,
    application_id     UUID           NOT NULL REFERENCES loan_application (id),
    document_type      VARCHAR(30)    NOT NULL,
    file_name          VARCHAR(255)   NOT NULL,
    storage_reference  VARCHAR(500)   NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    uploaded_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version            BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_loan_document_application_id ON loan_document (application_id);
--rollback DROP TABLE loan_document;
