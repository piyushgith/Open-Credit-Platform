--liquibase formatted sql

--changeset piyushprasad:023-create-credit-rule
CREATE TABLE credit_rule (
    id              UUID           PRIMARY KEY,
    policy_id       UUID           NOT NULL REFERENCES credit_policy (id),
    factor_code     VARCHAR(40)    NOT NULL,
    operator        VARCHAR(10)    NOT NULL,
    threshold_value NUMERIC(19,4)  NOT NULL,
    severity        VARCHAR(10)    NOT NULL,
    rule_order      INTEGER        NOT NULL,
    description     VARCHAR(200),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_credit_rule_policy_factor UNIQUE (policy_id, factor_code)
);

CREATE INDEX idx_credit_rule_policy_id ON credit_rule (policy_id);
--rollback DROP TABLE credit_rule;
