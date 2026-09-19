--liquibase formatted sql

--changeset piyushprasad:018-create-scorecard-rule
CREATE TABLE scorecard_rule (
    id            UUID           PRIMARY KEY,
    scorecard_id  UUID           NOT NULL REFERENCES scorecard (id),
    factor_code   VARCHAR(40)    NOT NULL,
    band_order    INTEGER        NOT NULL,
    min_value     NUMERIC(19,4),
    max_value     NUMERIC(19,4),
    points        INTEGER        NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_scorecard_rule_scorecard_factor_band UNIQUE (scorecard_id, factor_code, band_order)
);

CREATE INDEX idx_scorecard_rule_scorecard_id ON scorecard_rule (scorecard_id);
--rollback DROP TABLE scorecard_rule;
