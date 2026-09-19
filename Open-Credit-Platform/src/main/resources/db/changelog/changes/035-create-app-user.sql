--liquibase formatted sql

--changeset piyushprasad:035-create-app-user
CREATE TABLE app_user (
    id              UUID           PRIMARY KEY,
    username        VARCHAR(60)    NOT NULL UNIQUE,
    password_hash   VARCHAR(100)   NOT NULL,
    role            VARCHAR(16)    NOT NULL,
    approval_level  VARCHAR(32),
    active          BOOLEAN        NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);
--rollback DROP TABLE app_user;
