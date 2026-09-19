--liquibase formatted sql

--changeset piyushprasad:015-create-financial-ratio
CREATE TABLE financial_ratio (
    id               UUID           PRIMARY KEY,
    analysis_run_id  UUID           NOT NULL REFERENCES financial_analysis_run (id),
    ratio_code       VARCHAR(40)    NOT NULL,
    category         VARCHAR(40)    NOT NULL,
    value            NUMERIC(19,4)  NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_financial_ratio_run_code UNIQUE (analysis_run_id, ratio_code)
);

CREATE INDEX idx_financial_ratio_run_id ON financial_ratio (analysis_run_id);
--rollback DROP TABLE financial_ratio;
