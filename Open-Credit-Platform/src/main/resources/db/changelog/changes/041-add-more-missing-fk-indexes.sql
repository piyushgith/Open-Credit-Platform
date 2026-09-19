--liquibase formatted sql

--changeset piyushprasad:041-add-more-missing-fk-indexes
-- Week 12 database review (my_plan/week12-production-hardening-plan.md task 2), continued past the
-- two indexes 040 already fixed: loan_application.product_id and sanction.offer_id are real
-- foreign keys with no backing index at all (not even indirectly via a UNIQUE constraint, unlike
-- every other FK in this schema). "Which applications use product X" and "which sanction came from
-- offer Y" would both be full table scans without these.
CREATE INDEX idx_loan_application_product_id ON loan_application (product_id);
CREATE INDEX idx_sanction_offer_id ON sanction (offer_id);
--rollback DROP INDEX idx_loan_application_product_id; DROP INDEX idx_sanction_offer_id;
