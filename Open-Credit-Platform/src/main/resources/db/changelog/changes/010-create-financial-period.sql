--liquibase formatted sql

--changeset piyushprasad:010-create-financial-period
CREATE TABLE financial_period (
    id            UUID           PRIMARY KEY,
    period_label  VARCHAR(64)    NOT NULL,
    period_type   VARCHAR(20)    NOT NULL,
    start_date    DATE           NOT NULL,
    end_date      DATE           NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version       BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_financial_period_dates CHECK (start_date < end_date)
);
--rollback DROP TABLE financial_period;
