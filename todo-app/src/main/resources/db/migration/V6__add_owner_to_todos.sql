-- V6: Add ownership — user_id FK column on todos table.
-- Each todo now belongs to the user who created it.
--
-- WHY nullable?
-- Seed data rows from V3 have no user context (inserted by Flyway at startup,
-- not by an authenticated API call). Adding NOT NULL would fail because those
-- rows cannot provide a user_id value during this migration.
-- New todos created via the API will always have user_id set by the service layer.
--
-- WHY ON DELETE SET NULL instead of CASCADE or RESTRICT?
-- CASCADE: deleting a user would silently delete ALL their todos — dangerous.
-- RESTRICT: deleting a user would fail if they own any todos — blocks user removal.
-- SET NULL: deleting a user orphans their todos (user_id becomes null) — safest default.
-- A real app might use CASCADE with a soft-delete on users instead.
--
-- REFERENCES users(id): creates the foreign key constraint.
-- Flyway runs this on H2 and PostgreSQL — both support standard FK syntax.

ALTER TABLE todos ADD COLUMN user_id BIGINT REFERENCES users(id) ON DELETE SET NULL;
