--liquibase formatted sql

--changeset piyushprasad:004-seed-loan-products
INSERT INTO loan_product (id, product_type, name, min_amount, max_amount, min_tenure_months, max_tenure_months, active)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'PERSONAL', 'Personal Loan', 10000.00, 2000000.00, 6, 84, true),
    ('22222222-2222-2222-2222-222222222222', 'VEHICLE', 'Vehicle Loan', 50000.00, 5000000.00, 12, 84, true);
--rollback DELETE FROM loan_product WHERE product_type IN ('PERSONAL', 'VEHICLE');
