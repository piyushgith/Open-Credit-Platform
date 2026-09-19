--liquibase formatted sql

--changeset piyushprasad:030-create-approval-decision
CREATE TABLE approval_decision (
    id                  UUID           PRIMARY KEY,
    approval_case_id    UUID           NOT NULL REFERENCES approval_case (id),
    role                VARCHAR(16)    NOT NULL,
    actor_username      VARCHAR(120)   NOT NULL,
    actor_level         VARCHAR(32)    NOT NULL,
    outcome             VARCHAR(16)    NOT NULL,
    comment             VARCHAR(1000),
    decided_at          TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_approval_decision_case_role UNIQUE (approval_case_id, role)
);

-- The real guard against the same role acting twice on a case, including under concurrent
-- requests; the service's in-memory ApprovalLifecycle check is only the fast path.
CREATE INDEX idx_approval_decision_case_id ON approval_decision (approval_case_id);
--rollback DROP TABLE approval_decision;
