-- V4: Add role column to users table.
-- This migration adds RBAC support: each user now has a role of USER or ADMIN.
--
-- WHY DEFAULT 'USER'?
-- This column is NOT NULL. If we just added the column with no default, any
-- existing rows (from V3 seed or previous registrations) would violate the
-- NOT NULL constraint immediately. DEFAULT 'USER' ensures all existing rows
-- are backfilled with the default role at the moment this migration runs.
-- This is the standard safe pattern for adding a NOT NULL column to a table
-- that may already contain rows.
--
-- After this migration runs, new users created by AuthServiceImpl will have
-- their role set explicitly by Java code (Role.USER). The DB DEFAULT is a
-- safety net for direct SQL inserts and existing rows.
--
-- WHY VARCHAR(20) not TEXT?
-- Role values are short, known, and bounded. VARCHAR(20) communicates intent
-- and is slightly more efficient than TEXT for this use case.
-- The CHECK constraint enforces that only valid role values can be stored,
-- making the DB a second line of defence after Java enum validation.

ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

ALTER TABLE users
    ADD CONSTRAINT users_role_check CHECK (role IN ('USER', 'ADMIN'));
