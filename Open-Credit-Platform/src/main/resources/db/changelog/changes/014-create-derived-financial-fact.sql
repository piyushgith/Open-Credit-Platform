--liquibase formatted sql

--changeset piyushprasad:014-create-derived-financial-fact
CREATE TABLE derived_financial_fact (
    id               UUID           PRIMARY KEY,
    analysis_run_id  UUID           NOT NULL REFERENCES financial_analysis_run (id),
    fact_code        VARCHAR(40)    NOT NULL,
    value            NUMERIC(19,2)  NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_derived_financial_fact_run_code UNIQUE (analysis_run_id, fact_code)
);

CREATE INDEX idx_derived_financial_fact_run_id ON derived_financial_fact (analysis_run_id);
--rollback DROP TABLE derived_financial_fact;
