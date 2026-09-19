--liquibase formatted sql

--changeset piyushprasad:026-seed-default-credit-policy
-- Default credit policy, mirroring the roadmap's worked example:
-- Score >= 700 AND DSCR >= 1.5 AND Debt/EBITDA <= 4 (all HARD -> any failure declines),
-- plus two SOFT rules (a failure refers rather than declines).
INSERT INTO credit_policy (id, name, active)
VALUES ('77777777-7777-7777-7777-777777777701', 'STANDARD_CREDIT_POLICY_V1', true);

INSERT INTO credit_rule (id, policy_id, factor_code, operator, threshold_value, severity, rule_order, description) VALUES
    ('77777777-7777-7777-7777-777777777710', '77777777-7777-7777-7777-777777777701', 'TOTAL_SCORE', 'GTE', 700.0000, 'HARD', 1, 'Total score must be at least 700'),
    ('77777777-7777-7777-7777-777777777711', '77777777-7777-7777-7777-777777777701', 'DSCR', 'GTE', 1.5000, 'HARD', 2, 'DSCR must be at least 1.5'),
    ('77777777-7777-7777-7777-777777777712', '77777777-7777-7777-7777-777777777701', 'DEBT_TO_EBITDA', 'LTE', 4.0000, 'HARD', 3, 'Debt/EBITDA must not exceed 4.0'),
    ('77777777-7777-7777-7777-777777777713', '77777777-7777-7777-7777-777777777701', 'INTEREST_COVERAGE', 'GTE', 1.5000, 'SOFT', 4, 'Interest coverage should be at least 1.5'),
    ('77777777-7777-7777-7777-777777777714', '77777777-7777-7777-7777-777777777701', 'CURRENT_RATIO', 'GTE', 1.0000, 'SOFT', 5, 'Current ratio should be at least 1.0');
--rollback DELETE FROM credit_rule WHERE policy_id = '77777777-7777-7777-7777-777777777701';
--rollback DELETE FROM credit_policy WHERE id = '77777777-7777-7777-7777-777777777701';
