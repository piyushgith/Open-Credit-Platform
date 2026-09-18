--liquibase formatted sql

--changeset piyushprasad:012-create-financial-line-item
CREATE TABLE financial_line_item (
    id             UUID           PRIMARY KEY,
    statement_id   UUID           NOT NULL REFERENCES financial_statement (id),
    line_item_code VARCHAR(40)    NOT NULL,
    value          NUMERIC(19,2)  NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_financial_line_item_statement_code UNIQUE (statement_id, line_item_code)
);

CREATE INDEX idx_financial_line_item_statement_id ON financial_line_item (statement_id);
--rollback DROP TABLE financial_line_item;
