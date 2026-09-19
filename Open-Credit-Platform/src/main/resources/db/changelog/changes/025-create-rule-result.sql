--liquibase formatted sql

--changeset piyushprasad:025-create-rule-result
CREATE TABLE rule_result (
    id              UUID           PRIMARY KEY,
    decision_id     UUID           NOT NULL REFERENCES credit_decision (id),
    factor_code     VARCHAR(40)    NOT NULL,
    operator        VARCHAR(10)    NOT NULL,
    threshold_value NUMERIC(19,4)  NOT NULL,
    actual_value    NUMERIC(19,4)  NOT NULL,
    severity        VARCHAR(10)    NOT NULL,
    passed          BOOLEAN        NOT NULL,
    description     VARCHAR(200),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_rule_result_decision_factor UNIQUE (decision_id, factor_code)
);

CREATE INDEX idx_rule_result_decision_id ON rule_result (decision_id);
--rollback DROP TABLE rule_result;
