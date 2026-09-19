--liquibase formatted sql

--changeset piyushprasad:028-seed-default-authority-matrix
-- Worked example from the roadmap: small, low-risk approvals clear automatically; mid-size
-- amounts need a credit officer; everything else (including any amount at a weak risk grade)
-- falls through to the senior-credit-manager catch-all, which has no criteria and therefore
-- always matches, so resolution can never fail to find an entry.
INSERT INTO authority_matrix_entry (id, product_type, risk_grade, min_amount, max_amount, required_level, match_order, active) VALUES
    ('88888888-8888-8888-8888-888888888801', NULL, 'A', NULL, 500000.00, 'AUTO', 10, true),
    ('88888888-8888-8888-8888-888888888802', NULL, 'B', NULL, 500000.00, 'AUTO', 20, true),
    ('88888888-8888-8888-8888-888888888803', NULL, NULL, NULL, 2000000.00, 'CREDIT_OFFICER', 30, true),
    ('88888888-8888-8888-8888-888888888804', NULL, NULL, NULL, NULL, 'SENIOR_CREDIT_MANAGER', 100, true);
--rollback DELETE FROM authority_matrix_entry WHERE id IN ('88888888-8888-8888-8888-888888888801', '88888888-8888-8888-8888-888888888802', '88888888-8888-8888-8888-888888888803', '88888888-8888-8888-8888-888888888804');
