--liquibase formatted sql

--changeset piyushprasad:009-create-underwriting-attempt
CREATE TABLE underwriting_attempt (
    id                    UUID           PRIMARY KEY,
    underwriting_case_id  UUID           NOT NULL REFERENCES underwriting_case (id),
    cycle_number          INTEGER        NOT NULL,
    status                VARCHAR(20)    NOT NULL,
    active                BOOLEAN        NOT NULL DEFAULT true,
    decision_details      JSONB,
    started_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    completed_at          TIMESTAMPTZ,
    version               BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_underwriting_attempt_case_cycle UNIQUE (underwriting_case_id, cycle_number)
);

-- Enforces "only one active underwriting attempt per case" at the database level, on top of
-- the service deactivating the previous attempt (flushed) before inserting a new one.
CREATE UNIQUE INDEX uq_underwriting_attempt_active_per_case
    ON underwriting_attempt (underwriting_case_id)
    WHERE active;

CREATE INDEX idx_underwriting_attempt_case_id ON underwriting_attempt (underwriting_case_id);
--rollback DROP TABLE underwriting_attempt;
