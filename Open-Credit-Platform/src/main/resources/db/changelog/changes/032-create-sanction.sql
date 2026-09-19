--liquibase formatted sql

--changeset piyushprasad:032-create-sanction
CREATE TABLE sanction (
    id                  UUID           PRIMARY KEY,
    application_id      UUID           NOT NULL UNIQUE REFERENCES loan_application (id),
    offer_id            UUID           NOT NULL REFERENCES offer (id),
    sanctioned_amount   NUMERIC(19,2)  NOT NULL CHECK (sanctioned_amount > 0),
    interest_rate       NUMERIC(6,3)   NOT NULL,
    tenure_months       INTEGER        NOT NULL CHECK (tenure_months > 0),
    monthly_emi         NUMERIC(19,2)  NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now()
);
--rollback DROP TABLE sanction;
