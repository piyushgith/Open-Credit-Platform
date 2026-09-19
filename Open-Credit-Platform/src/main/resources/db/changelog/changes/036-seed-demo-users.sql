--liquibase formatted sql

--changeset piyushprasad:036-seed-demo-users
-- Demo-only logins (Week 10 has no self-registration flow) so the maker-checker and admin-config
-- endpoints have something to authenticate against. Passwords are the username's local part plus
-- "123" (e.g. alice.maker / maker123); hashes are BCrypt (strength 10). Rotate or remove these
-- before any real deployment.
-- admin: role ADMIN, approvalLevel SENIOR_CREDIT_MANAGER (config access, and a checker override).
-- alice.maker: role MAKER, no approvalLevel (unused for a maker).
-- bob.checker: role CHECKER, approvalLevel CREDIT_OFFICER (mid-size cases).
-- carol.checker: role CHECKER, approvalLevel SENIOR_CREDIT_MANAGER (the catch-all tier).
INSERT INTO app_user (id, username, password_hash, role, approval_level, active) VALUES
    ('99999999-9999-9999-9999-999999999901', 'admin', '$2a$10$L/dKc0dYdZuOLusugWJ85e920EkqVzDSPnrBQGrEiERsbWa9y13yi', 'ADMIN', 'SENIOR_CREDIT_MANAGER', true),
    ('99999999-9999-9999-9999-999999999902', 'alice.maker', '$2a$10$eynEDqae9I.oqG.cksOmL.XyUAeyyqi0F8oOS9v.EOhnh//VRqhRG', 'MAKER', NULL, true),
    ('99999999-9999-9999-9999-999999999903', 'bob.checker', '$2a$10$1xM6rJGF/0TCNzqUQkHcE.VhDS2VTAE2o7pGZk/R61twmyVCkeqBm', 'CHECKER', 'CREDIT_OFFICER', true),
    ('99999999-9999-9999-9999-999999999904', 'carol.checker', '$2a$10$UzrbUaMgLp82qXTlwsRp5OU9GNnsdi7VhQU4Q6XADOr4ge4WaY4Vu', 'CHECKER', 'SENIOR_CREDIT_MANAGER', true);
--rollback DELETE FROM app_user WHERE id IN ('99999999-9999-9999-9999-999999999901', '99999999-9999-9999-9999-999999999902', '99999999-9999-9999-9999-999999999903', '99999999-9999-9999-9999-999999999904');
