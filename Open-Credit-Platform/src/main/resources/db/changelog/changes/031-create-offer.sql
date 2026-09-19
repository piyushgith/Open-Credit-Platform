--liquibase formatted sql

--changeset piyushprasad:031-create-offer
CREATE TABLE offer (
    id                UUID           PRIMARY KEY,
    application_id    UUID           NOT NULL REFERENCES loan_application (id),
    offer_amount      NUMERIC(19,2)  NOT NULL CHECK (offer_amount > 0),
    interest_rate     NUMERIC(6,3)   NOT NULL,
    tenure_months     INTEGER        NOT NULL CHECK (tenure_months > 0),
    monthly_emi       NUMERIC(19,2)  NOT NULL,
    status            VARCHAR(16)    NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version           BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_offer_application_id ON offer (application_id);

-- The real guard against selecting more than one offer per application, including under
-- concurrent requests; the service's existsByApplicationIdAndStatus check is only the fast path.
CREATE UNIQUE INDEX uq_offer_selected_per_application ON offer (application_id) WHERE status = 'SELECTED';
--rollback DROP TABLE offer;
