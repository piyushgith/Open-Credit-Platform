--liquibase formatted sql

--changeset piyushprasad:016-create-risk-indicator
CREATE TABLE risk_indicator (
    id               UUID           PRIMARY KEY,
    analysis_run_id  UUID           NOT NULL REFERENCES financial_analysis_run (id),
    indicator_code   VARCHAR(40)    NOT NULL,
    triggered        BOOLEAN        NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_risk_indicator_run_code UNIQUE (analysis_run_id, indicator_code)
);

CREATE INDEX idx_risk_indicator_run_id ON risk_indicator (analysis_run_id);
--rollback DROP TABLE risk_indicator;
