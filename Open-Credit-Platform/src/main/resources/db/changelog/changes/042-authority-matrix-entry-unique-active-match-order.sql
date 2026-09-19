--liquibase formatted sql

--changeset piyushprasad:042-authority-matrix-entry-unique-active-match-order
-- AuthorityMatrixResolver.resolve() sorts active entries by match_order and takes the first match;
-- nothing enforced match_order was unique among active entries, so two active entries with the same
-- match_order made authority resolution non-deterministic (tie broken by whatever row order
-- Postgres happened to return). Mirrors uq_scorecard_active/uq_credit_policy_active: this DB
-- constraint is the real guard, AuthorityMatrixAdminService's pre-check is only the fast path.
CREATE UNIQUE INDEX uq_authority_matrix_entry_active_match_order ON authority_matrix_entry (match_order) WHERE active;
--rollback DROP INDEX uq_authority_matrix_entry_active_match_order;
