--liquibase formatted sql

--changeset piyushprasad:044-rule-result-actual-value-nullable
-- DecisionEngine records a HARD rule whose input is missing as not passed, with no actual value.
ALTER TABLE rule_result ALTER COLUMN actual_value DROP NOT NULL;
--rollback ALTER TABLE rule_result ALTER COLUMN actual_value SET NOT NULL;
