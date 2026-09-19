--liquibase formatted sql

--changeset piyushprasad:019-create-score
CREATE TABLE score (
    id               UUID           PRIMARY KEY,
    analysis_run_id  UUID           NOT NULL REFERENCES financial_analysis_run (id),
    scorecard_id     UUID           NOT NULL REFERENCES scorecard (id),
    total_score      INTEGER        NOT NULL,
    risk_grade       VARCHAR(1)     NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_score_run_scorecard UNIQUE (analysis_run_id, scorecard_id)
);

CREATE INDEX idx_score_analysis_run_id ON score (analysis_run_id);
--rollback DROP TABLE score;
