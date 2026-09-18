--liquibase formatted sql

--changeset piyushprasad:005-alter-loan-application-lifecycle
-- Pre-dates customer/product linkage and predates any real usage of this table;
-- nothing to backfill in dev.
DELETE FROM loan_application;

ALTER TABLE loan_application
    ADD COLUMN customer_id UUID,
    ADD COLUMN product_id UUID;

ALTER TABLE loan_application
    ALTER COLUMN decision DROP NOT NULL,
    ALTER COLUMN decision_details DROP NOT NULL;

ALTER TABLE loan_application
    ADD CONSTRAINT fk_loan_application_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    ADD CONSTRAINT fk_loan_application_product FOREIGN KEY (product_id) REFERENCES loan_product (id);

ALTER TABLE loan_application
    ALTER COLUMN customer_id SET NOT NULL,
    ALTER COLUMN product_id SET NOT NULL;

CREATE INDEX idx_loan_application_customer_id ON loan_application (customer_id);
CREATE INDEX idx_loan_application_status ON loan_application (status);
--rollback ALTER TABLE loan_application DROP CONSTRAINT fk_loan_application_customer, DROP CONSTRAINT fk_loan_application_product, DROP COLUMN customer_id, DROP COLUMN product_id;
