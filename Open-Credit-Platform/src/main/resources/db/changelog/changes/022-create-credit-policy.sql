--liquibase formatted sql

--changeset piyushprasad:022-create-credit-policy
CREATE TABLE credit_policy (
    id          UUID           PRIMARY KEY,
    name        VARCHAR(80)    NOT NULL,
    active      BOOLEAN        NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version     BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_credit_policy_name UNIQUE (name)
);

-- Enforces "only one active credit policy" at the database level, same pattern as
-- uq_scorecard_active: the service deactivates the previous active policy (flushed) before
-- activating a new one.
CREATE UNIQUE INDEX uq_credit_policy_active ON credit_policy (active) WHERE active;
--rollback DROP TABLE credit_policy;
