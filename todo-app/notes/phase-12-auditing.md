# Phase 12 — JPA Auditing

## Goal
Automatically populate `createdAt` and `updatedAt` on every entity (Todo, User)
without any manual code in services or controllers. Uses Spring Data JPA's
`AuditingEntityListener` and `@MappedSuperclass` pattern.

---

## New Files

| File | Purpose |
|------|---------|
| `model/Auditable.java` | Abstract base class with `createdAt` + `updatedAt` fields |
| `config/JpaConfig.java` | `@EnableJpaAuditing` — activates the auditing infrastructure |

---

## Modified Files

| File | Change |
|------|--------|
| `model/Todo.java` | `extends Auditable`, added `@EqualsAndHashCode(callSuper=false)` |
| `model/User.java` | `extends Auditable`, added `@EqualsAndHashCode(callSuper=false)` |
| `dto/response/TodoResponse.java` | Added `createdAt`, `updatedAt` with `@JsonFormat` |
| `service/TodoMapper.java` | Added `unmappedTargetPolicy = ReportingPolicy.IGNORE`, updated `toResponse` comment |
| `data.sql` | Added `created_at`, `updated_at` columns to seed INSERT |

---

## Key Concepts

### `@MappedSuperclass`
- JPA includes fields from this class in each subclass's table
- Does NOT create its own table (unlike `@Inheritance`)
- Correct pattern for shared audit fields across multiple entities

### `@EntityListeners(AuditingEntityListener.class)`
- Registers Spring's listener on every entity extending `Auditable`
- Fires on `@PrePersist` → sets `createdAt` AND `updatedAt`
- Fires on `@PreUpdate` → sets ONLY `updatedAt`

### `@CreatedDate` vs `@LastModifiedDate`
- `@CreatedDate`: set once, never updated. `updatable = false` in `@Column` enforces this at DB level
- `@LastModifiedDate`: set on every save (insert + update)

### `@EnableJpaAuditing`
- Must be on a `@Configuration` class
- Lives in its own `JpaConfig` class — SRP (not mixed with SecurityConfig)

### `@EqualsAndHashCode(callSuper = false)`
- Prevents Lombok from including `createdAt`/`updatedAt` in `equals`/`hashCode`
- Entity equality should be based on `id`, not timestamps
- Required when a class using `@Data` extends a non-Object superclass

### `unmappedTargetPolicy = ReportingPolicy.IGNORE` on `@Mapper`
- Suppresses MapStruct build warnings for `createdAt`/`updatedAt`
- These fields exist as setters on `Todo` (from `Auditable`) but have no corresponding
  source fields in `CreateTodoRequest` or `UpdateTodoRequest`
- They're set by `AuditingEntityListener`, not by the mapper

### `@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")`
- Without this, Jackson serializes `LocalDateTime` as an array: `[2024,1,15,10,30,0]`
- With this, it produces: `"2024-01-15T10:30:00"`
- Always add this to `LocalDateTime` fields in response DTOs

### `data.sql` — Why timestamps are needed in seed INSERT
- `data.sql` runs raw SQL — it does NOT trigger JPA `@PrePersist` events
- `AuditingEntityListener` only fires when you call `repository.save()` in Java code
- For manually seeded rows, provide `NOW()` explicitly

---

## How the Listener Works Internally

```
repository.save(newTodo)
  → Hibernate @PrePersist
    → AuditingEntityListener.touchForCreate()
      → checks: if createdAt == null → set createdAt = LocalDateTime.now()
      → always: set updatedAt = LocalDateTime.now()
  → SQL: INSERT INTO todos (title, ..., created_at, updated_at) VALUES (...)

repository.save(existingTodo)
  → Hibernate @PreUpdate
    → AuditingEntityListener.touchForUpdate()
      → checks: createdAt != null → SKIP (preserve original)
      → always: set updatedAt = LocalDateTime.now()
  → SQL: UPDATE todos SET title=?, ..., updated_at=? WHERE id=?
  — NOTE: created_at is NOT in the UPDATE because @Column(updatable=false)
```

---

## API Response (after Phase 12)

```json
{
  "id": 1,
  "title": "Learn Spring Boot",
  "description": "Complete all phases",
  "completed": false,
  "createdAt": "2024-01-15T10:30:00",
  "updatedAt": "2024-01-15T11:00:00"
}
```

---

## Common Interview Questions

**Q: What is `@MappedSuperclass` vs `@Inheritance`?**
`@MappedSuperclass`: fields merged into child tables, no separate parent table, no polymorphic queries.
`@Inheritance`: supports polymorphic queries, multiple table strategies (JOINED, SINGLE_TABLE, TABLE_PER_CLASS).
Use `@MappedSuperclass` for audit fields — it's simpler and more performant.

**Q: Why is `@EnableJpaAuditing` in a separate `JpaConfig` and not in the main class?**
Single Responsibility Principle. `TodoApplication` is the bootstrap entry point.
`SecurityConfig` handles security. `JpaConfig` handles JPA behavior.
Each class has one reason to change.

**Q: Why `@Column(updatable = false)` on `createdAt`?**
Defense in depth. If code accidentally calls `save()` multiple times on an existing entity,
the DB column definition itself rejects any UPDATE to `created_at`. The `@CreatedDate` listener
also skips it (checks for non-null), but the DB constraint is a second safety net.

**Q: Why `callSuper = false` on `@EqualsAndHashCode`?**
Entity equality should be based on business identity (usually `id`), not on audit metadata.
Two `Todo` objects with the same `id` but different `updatedAt` represent the same entity.
Including timestamps in `equals` would break `Set` collections and Hibernate's caching.

---

## Test Results
```
TodoControllerTest    : 13 tests ✅
TodoMapperTest        :  6 tests ✅
TodoServiceImplTest   : 10 tests ✅
TodoApplicationTest   :  1 test  ✅
─────────────────────────────────
Total                 : 30 tests ✅  BUILD SUCCESS (no warnings)
```
