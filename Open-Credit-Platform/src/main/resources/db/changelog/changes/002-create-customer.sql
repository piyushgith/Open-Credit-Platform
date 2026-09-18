--liquibase formatted sql

--changeset piyushprasad:002-create-customer
CREATE TABLE customer (
    id                 UUID           PRIMARY KEY,
    full_name          VARCHAR(160)   NOT NULL,
    email              VARCHAR(160)   NOT NULL,
    phone_number       VARCHAR(20)    NOT NULL,
    date_of_birth      DATE           NOT NULL,
    pan_number         VARCHAR(10)    NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version            BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_customer_email UNIQUE (email),
    CONSTRAINT uq_customer_phone_number UNIQUE (phone_number),
    CONSTRAINT uq_customer_pan_number UNIQUE (pan_number)
);
--rollback DROP TABLE customer;
