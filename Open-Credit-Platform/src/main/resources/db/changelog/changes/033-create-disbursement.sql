--liquibase formatted sql

--changeset piyushprasad:033-create-disbursement
CREATE TABLE disbursement (
    id                UUID           PRIMARY KEY,
    sanction_id       UUID           NOT NULL UNIQUE REFERENCES sanction (id),
    disbursed_total   NUMERIC(19,2)  NOT NULL DEFAULT 0 CHECK (disbursed_total >= 0),
    status            VARCHAR(16)    NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version           BIGINT         NOT NULL DEFAULT 0
);
--rollback DROP TABLE disbursement;
