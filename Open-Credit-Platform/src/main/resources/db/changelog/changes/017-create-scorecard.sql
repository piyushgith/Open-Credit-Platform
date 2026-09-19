--liquibase formatted sql

--changeset piyushprasad:017-create-scorecard
CREATE TABLE scorecard (
    id          UUID           PRIMARY KEY,
    name        VARCHAR(80)    NOT NULL,
    active      BOOLEAN        NOT NULL DEFAULT false,
    base_score  INTEGER        NOT NULL,
    min_score   INTEGER        NOT NULL,
    max_score   INTEGER        NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version     BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_scorecard_name UNIQUE (name)
);

-- Enforces "only one active scorecard" at the database level, same pattern as
-- uq_underwriting_attempt_active_per_case: the service deactivates the previous active
-- scorecard (flushed) before activating a new one.
CREATE UNIQUE INDEX uq_scorecard_active ON scorecard (active) WHERE active;
--rollback DROP TABLE scorecard;
