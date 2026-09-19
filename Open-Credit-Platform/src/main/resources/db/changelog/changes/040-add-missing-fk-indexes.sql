--liquibase formatted sql

--changeset piyushprasad:040-add-missing-fk-indexes
-- score.scorecard_id and credit_decision.policy_id are real foreign keys backing real reporting
-- queries ("which scores used scorecard X", "which decisions were made under policy Y" — the
-- admin-config controllers' natural follow-up question), but each sits as the *second* column of
-- a composite UNIQUE constraint (uq_score_run_scorecard, uq_credit_decision_score_policy), whose
-- automatic index only serves lookups on the leading column. Neither had a dedicated index.
CREATE INDEX idx_score_scorecard_id ON score (scorecard_id);
CREATE INDEX idx_credit_decision_policy_id ON credit_decision (policy_id);
--rollback DROP INDEX idx_score_scorecard_id; DROP INDEX idx_credit_decision_policy_id;
