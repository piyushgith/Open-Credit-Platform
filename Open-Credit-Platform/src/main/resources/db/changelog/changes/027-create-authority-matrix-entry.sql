--liquibase formatted sql

--changeset piyushprasad:027-create-authority-matrix-entry
CREATE TABLE authority_matrix_entry (
    id                UUID           PRIMARY KEY,
    product_type      VARCHAR(32),
    risk_grade        VARCHAR(8),
    min_amount        NUMERIC(19,2),
    max_amount        NUMERIC(19,2),
    required_level    VARCHAR(32)    NOT NULL,
    match_order       INTEGER        NOT NULL,
    active            BOOLEAN        NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CHECK (min_amount IS NULL OR max_amount IS NULL OR min_amount <= max_amount)
);

CREATE INDEX idx_authority_matrix_entry_active_order ON authority_matrix_entry (active, match_order);
--rollback DROP TABLE authority_matrix_entry;
