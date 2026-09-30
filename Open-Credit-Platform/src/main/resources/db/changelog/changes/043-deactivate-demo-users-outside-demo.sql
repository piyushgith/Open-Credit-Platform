--liquibase formatted sql

--changeset piyushprasad:043-deactivate-demo-users-outside-demo context:!demo
-- 036 seeds four logins with published passwords. Outside the demo context (application-dev.properties)
-- they are deactivated, so AuthService rejects them.
UPDATE app_user SET active = false
WHERE id IN ('99999999-9999-9999-9999-999999999901',
             '99999999-9999-9999-9999-999999999902',
             '99999999-9999-9999-9999-999999999903',
             '99999999-9999-9999-9999-999999999904');
--rollback UPDATE app_user SET active = true WHERE id IN ('99999999-9999-9999-9999-999999999901', '99999999-9999-9999-9999-999999999902', '99999999-9999-9999-9999-999999999903', '99999999-9999-9999-9999-999999999904');
