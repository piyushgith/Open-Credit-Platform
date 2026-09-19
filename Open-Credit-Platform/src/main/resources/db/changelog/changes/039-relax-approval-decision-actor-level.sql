--liquibase formatted sql

--changeset piyushprasad:039-relax-approval-decision-actor-level
-- Before Week 10, actorLevel was always client-supplied, including a meaningless value for MAKER
-- rows. It now comes from the authenticated AppUser (security.model.AppUser.approvalLevel), which
-- is legitimately null for a MAKER — only a CHECKER's level is ever compared against a case's
-- requiredLevel. NOT NULL was enforcing a value that was never meaningful for half the rows.
ALTER TABLE approval_decision ALTER COLUMN actor_level DROP NOT NULL;
--rollback ALTER TABLE approval_decision ALTER COLUMN actor_level SET NOT NULL;
