--liquibase formatted sql

--changeset piyushprasad:024-create-credit-decision
CREATE TABLE credit_decision (
    id                  UUID           PRIMARY KEY,
    score_id            UUID           NOT NULL REFERENCES score (id),
    policy_id           UUID           NOT NULL REFERENCES credit_policy (id),
    outcome             VARCHAR(10)    NOT NULL,
    required_authority  VARCHAR(30)    NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_credit_decision_score_policy UNIQUE (score_id, policy_id)
);

CREATE INDEX idx_credit_decision_score_id ON credit_decision (score_id);
--rollback DROP TABLE credit_decision;
