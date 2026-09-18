--liquibase formatted sql

--changeset piyushprasad:013-create-financial-analysis-run
CREATE TABLE financial_analysis_run (
    id            UUID           PRIMARY KEY,
    statement_id  UUID           NOT NULL REFERENCES financial_statement (id),
    run_at        TIMESTAMPTZ    NOT NULL,
    version       BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_financial_analysis_run_statement_id ON financial_analysis_run (statement_id);
--rollback DROP TABLE financial_analysis_run;
