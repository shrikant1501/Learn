# Phase 14: Flyway Database Migrations

## Goal
Replace Hibernate's automatic schema management (`ddl-auto=update/create-drop`) with
Flyway — a versioned SQL migration tool that provides production-safe, auditable,
reproducible schema evolution.

## What Changed

### Files Added
- `src/main/resources/db/migration/V1__create_todos.sql` — creates todos table
- `src/main/resources/db/migration/V2__create_users.sql` — creates users table
- `src/main/resources/db/migration/V3__seed_todos.sql`   — seed todos (replaces data.sql)

### Files Modified
- `pom.xml` — added `flyway-core` dependency (version managed by Spring Boot BOM)
- `application.properties` — added `spring.flyway.enabled=true`, removed `defer-datasource-initialization`
- `application-local.properties` — changed `ddl-auto=update` → `none`, added flyway config
- `application-docker.properties` — changed `ddl-auto=update` → `none`, added flyway config

### Files Deleted
- `src/main/resources/data.sql` — replaced by `V3__seed_todos.sql`

## Key Concepts

### Flyway Naming Convention
```
V{version}__{description}.sql
  ^          ^^
  |          Two underscores (REQUIRED)
  Version (1, 2, 1.1, 20231015, etc.)
```

### flyway_schema_history table
Flyway creates and manages this table automatically. It records:
- `version` — the migration version number
- `description` — human-readable description from filename
- `script` — the filename
- `checksum` — CRC32 hash of the script content
- `installed_on` — timestamp when migration was applied
- `success` — whether the migration succeeded

### Profile behaviour after Phase 14

| Profile | ddl-auto | Flyway | Behaviour |
|---------|----------|--------|-----------|
| default (H2) | create-drop | enabled | Hibernate creates schema, Flyway creates history + seeds |
| local (PostgreSQL) | none | enabled | Flyway is fully in charge of schema |
| docker (PostgreSQL) | none | enabled | Flyway is fully in charge of schema |
| @WebMvcTest | N/A | N/A | No DB loaded — mocked service layer |

### The Golden Rule of Flyway
**NEVER edit or delete an already-applied migration script.**
If Flyway detects a checksum mismatch, it throws an error and refuses to start.
To fix a mistake: create a NEW migration (V4, V5...) that applies the correction.

### Why ddl-auto=none for PostgreSQL?
- `update` — Hibernate adds columns but never drops them (schema drift)
- `validate` — Hibernate checks schema matches entities (stricter, good too)
- `none` — Hibernate is completely hands-off (Flyway owns everything)
For production, `none` is the correct choice when using a migration tool.

### Why keep ddl-auto=create-drop for H2?
In the H2 in-memory database (default profile + tests):
- H2 is wiped on every restart anyway (in-memory)
- Flyway's `IF NOT EXISTS` DDL handles the case where Hibernate creates tables first
- The behaviour is consistent: Flyway always runs all 3 migrations on a clean H2 DB
- Flyway history table is also wiped — so it's a clean slate every time

## Test Run Output (BUILD SUCCESS)
```
Flyway Community Edition 9.22.3
Database: jdbc:h2:mem:tododb (H2 2.2)
Creating Schema History table "PUBLIC"."flyway_schema_history" ...
Current version of schema "PUBLIC": << Empty Schema >>
Migrating schema "PUBLIC" to version "1 - create todos"
Migrating schema "PUBLIC" to version "2 - create users"
Migrating schema "PUBLIC" to version "3 - seed todos"
Successfully applied 3 migrations to schema "PUBLIC", now at version v3

Tests run: 30, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Common Interview Questions

**Q: What is the difference between Flyway and Liquibase?**
Both are schema migration tools. Flyway uses plain SQL scripts (simpler, more explicit).
Liquibase uses XML/YAML/JSON changelogs (more abstract, supports rollback definitions).
For teams that love SQL and simplicity, Flyway is the common choice.

**Q: What happens if you change an already-applied migration file?**
Flyway calculates a CRC32 checksum of each script when it runs. On the next startup,
it recalculates the checksum and compares. A mismatch causes Flyway to throw
`FlywayMigrationCheckException` and the application refuses to start. This is a
safety feature — it ensures all environments ran the exact same SQL.

**Q: How do you handle rollbacks with Flyway?**
Flyway Community Edition does NOT support automatic rollbacks. The recommended pattern
is "forward-only migrations": if V3 introduced a bug, you write V4 that corrects it.
Flyway Teams edition has `undo` migrations, but most teams use the forward-only approach.

**Q: Why use `ddl-auto=none` instead of `validate` with Flyway?**
`validate` is also valid and adds a safety check — Hibernate confirms the DB schema
matches the entities on startup. `none` is slightly faster and trusts Flyway completely.
In CI/CD pipelines, `validate` is useful to catch accidental entity/migration drift.

**Q: What is `baseline-on-migrate` in Flyway?**
If you add Flyway to an existing database that already has tables (no migration history),
Flyway refuses to start because it sees a non-empty schema with no history.
`baseline-on-migrate=true` tells Flyway: "assume everything up to baseline version is
already applied, start tracking from here." Used when retrofitting Flyway onto legacy DBs.

## Gotchas Solved in This Phase

1. **`IF NOT EXISTS` in DDL**: Both V1 and V2 use `CREATE TABLE IF NOT EXISTS`.
   With H2 `ddl-auto=create-drop`, Hibernate creates the tables first.
   Flyway's `IF NOT EXISTS` skips silently — no conflict. ✅

2. **`BIGINT GENERATED BY DEFAULT AS IDENTITY`**: ANSI SQL 2003 syntax.
   Works on both H2 (PostgreSQL mode) and PostgreSQL 10+. Avoids `SERIAL` which
   is PostgreSQL-specific and not understood by H2. ✅

3. **`data.sql` removed**: With Flyway enabled, `data.sql` would re-insert seed rows
   on every restart after the schema was created. Removing it avoids duplicate key errors. ✅

## Possible Improvements for Future Phases

- Add `spring.flyway.validate-on-migrate=true` (explicit validation)
- Add `spring.flyway.out-of-order=false` (default, but good to document)
- Phase 15+: create `V4__add_column_priority.sql` to practise adding a column
- Consider `@FlywayDataSource` for multi-datasource setups
- Use `flyway.repair()` in CI to fix a botched migration in a non-prod environment

## Small Exercises

1. Run the app with `--spring.flyway.enabled=false` — observe that Hibernate's
   `create-drop` still creates tables, but there is no seed data. Why?

2. After building, open `h2-console`, query `SELECT * FROM flyway_schema_history`.
   What columns do you see? What does the `checksum` column contain?

3. Add a `V4__add_priority_column.sql` with:
   `ALTER TABLE todos ADD COLUMN priority INT NOT NULL DEFAULT 0;`
   Run the app. Check `flyway_schema_history` — what is the new row?

4. Try editing `V3__seed_todos.sql` (change a description), then restart the app.
   What error does Flyway throw? What is the message about checksums?

5. What would happen if you set `ddl-auto=validate` instead of `none` for PostgreSQL?
   Would it work? What would Flyway + validate give you that `none` doesn't?
