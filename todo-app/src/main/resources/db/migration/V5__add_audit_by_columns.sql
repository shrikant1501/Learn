-- V5: Add created_by and last_modified_by audit columns to todos and users.
-- These columns record WHICH USER created or last modified each row.
-- Complements the existing created_at / updated_at (WHEN) columns from V1/V2.
--
-- WHY nullable?
-- Flyway seed data rows (V3) are inserted with no authenticated user in context.
-- AuditorAware returns Optional.empty() during startup — JPA leaves these columns null.
-- All rows created via the API (after startup) will have these columns populated.
--
-- WHY VARCHAR(100)?
-- Usernames are max 50 chars (@Size(max=50) on RegisterRequest).
-- 100 gives a comfortable safety margin.

-- H2 does not support multi-column ALTER TABLE ADD COLUMN in one statement.
-- Each column is added with its own statement (works on both H2 and PostgreSQL).
ALTER TABLE todos ADD COLUMN created_by       VARCHAR(100);
ALTER TABLE todos ADD COLUMN last_modified_by VARCHAR(100);

ALTER TABLE users ADD COLUMN created_by       VARCHAR(100);
ALTER TABLE users ADD COLUMN last_modified_by VARCHAR(100);
