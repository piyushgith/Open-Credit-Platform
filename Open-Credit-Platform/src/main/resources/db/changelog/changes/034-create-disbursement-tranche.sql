--liquibase formatted sql

--changeset piyushprasad:034-create-disbursement-tranche
CREATE TABLE disbursement_tranche (
    id                  UUID           PRIMARY KEY,
    disbursement_id     UUID           NOT NULL REFERENCES disbursement (id),
    tranche_number      INTEGER        NOT NULL CHECK (tranche_number > 0),
    amount              NUMERIC(19,2)  NOT NULL CHECK (amount > 0),
    request_reference   VARCHAR(120)   NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_disbursement_tranche_request UNIQUE (disbursement_id, request_reference)
);

-- The real guard against a duplicate disbursement request, including under concurrent retries;
-- DisbursementService's insert-and-catch is only the fast path.
CREATE INDEX idx_disbursement_tranche_disbursement_id ON disbursement_tranche (disbursement_id);
--rollback DROP TABLE disbursement_tranche;
