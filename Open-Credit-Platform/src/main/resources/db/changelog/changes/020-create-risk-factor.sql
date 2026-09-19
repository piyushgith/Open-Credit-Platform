--liquibase formatted sql

--changeset piyushprasad:020-create-risk-factor
CREATE TABLE risk_factor (
    id           UUID           PRIMARY KEY,
    score_id     UUID           NOT NULL REFERENCES score (id),
    factor_code  VARCHAR(40)    NOT NULL,
    value        NUMERIC(19,4)  NOT NULL,
    points       INTEGER        NOT NULL,
    description  VARCHAR(200),
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_risk_factor_score_code UNIQUE (score_id, factor_code)
);

CREATE INDEX idx_risk_factor_score_id ON risk_factor (score_id);
--rollback DROP TABLE risk_factor;
