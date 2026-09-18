--liquibase formatted sql

--changeset piyushprasad:003-create-loan-product
CREATE TABLE loan_product (
    id                  UUID           PRIMARY KEY,
    product_type        VARCHAR(32)    NOT NULL,
    name                VARCHAR(120)   NOT NULL,
    min_amount          NUMERIC(19,2)  NOT NULL,
    max_amount          NUMERIC(19,2)  NOT NULL,
    min_tenure_months   INTEGER        NOT NULL,
    max_tenure_months   INTEGER        NOT NULL,
    active              BOOLEAN        NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version             BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_loan_product_product_type UNIQUE (product_type),
    CONSTRAINT chk_loan_product_min_amount CHECK (min_amount > 0),
    CONSTRAINT chk_loan_product_max_amount CHECK (max_amount >= min_amount),
    CONSTRAINT chk_loan_product_min_tenure CHECK (min_tenure_months > 0),
    CONSTRAINT chk_loan_product_max_tenure CHECK (max_tenure_months >= min_tenure_months)
);
--rollback DROP TABLE loan_product;
