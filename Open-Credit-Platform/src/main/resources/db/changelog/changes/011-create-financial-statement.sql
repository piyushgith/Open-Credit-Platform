--liquibase formatted sql

--changeset piyushprasad:011-create-financial-statement
CREATE TABLE financial_statement (
    id             UUID           PRIMARY KEY,
    application_id UUID           NOT NULL REFERENCES loan_application (id),
    period_id      UUID           NOT NULL UNIQUE REFERENCES financial_period (id),
    submitted_at   TIMESTAMPTZ    NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version        BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_financial_statement_application_id ON financial_statement (application_id);
--rollback DROP TABLE financial_statement;
