# Phase 22 — Todo Ownership

## Goal
Bind every todo to the user who created it. Enforce ownership on all CRUD operations:
- **USER role**: can only see, update, and delete their own todos
- **ADMIN role**: unrestricted access to all todos
- Todos show `ownedBy` (username) in the response
- Returning **404** (not 403) for unowned resources — prevents resource enumeration

---

## Concepts Learned

### @ManyToOne — the JPA Foreign Key
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id")
private User owner;
```
- `@ManyToOne`: many todos can belong to one user — this side holds the FK column
- `@JoinColumn(name = "user_id")`: maps to the `user_id` column added by V6 migration
- `FetchType.LAZY`: Hibernate does NOT join the `users` table on every `SELECT * FROM todos`.
  The `User` is only loaded when `todo.getOwner()` is first called.
  This is the correct default — prevents unnecessary JOINs and N+1 query surprises.
- **EAGER vs LAZY**: EAGER = always JOIN (expensive for collections); LAZY = JOIN on demand (efficient)

### Flyway Migration Strategy for Nullable FK
Adding a NOT NULL FK column to a table with existing rows is a **multi-step migration** in production:
1. Add column as nullable (V6 — what we did)
2. Backfill existing rows with a default user or mark as orphaned
3. Optionally add NOT NULL constraint in V7

We chose nullable because seed data rows have no owner, and they are test/development data. The service layer guarantees new rows always have an owner.

### 404 vs 403 for Ownership Violations
Returning `403 Forbidden` on `GET /todos/42` when you don't own it confirms:
- The resource with ID 42 **exists**
- You don't have access to it

An attacker can enumerate all valid IDs. Returning `404 Not Found` leaks no information — the resource might not exist at all. This is standard practice in REST APIs handling ownership.

### Spring Data Derived Query Method
```java
Optional<Todo> findByIdAndOwner(Long id, User owner);
```
Spring Data reads the method name and generates SQL automatically:
```sql
SELECT * FROM todos WHERE id = ? AND user_id = ?
```
No `@Query` annotation, no JPQL, no SQL — just a method name following the convention.

### Specification + Owner Filter Composition
`TodoSpecification.fromFilter(filter, owner)`:
- `owner = null` (ADMIN) → no `WHERE user_id =` predicate added → all todos visible
- `owner = User` (USER) → `AND user_id = ?` is appended → scoped to owner

This keeps the same `findAll(spec, pageable)` call in the service — no separate repository methods per role.

### SecurityUtil Interface Pattern (same lesson as JwtService)
`SecurityUtil` is an interface; `SecurityUtilImpl` is the `@Component` implementation.
- **Tests**: mock the interface via JDK proxy — no ByteBuddy needed on Java 25
- **Production**: Spring wires `SecurityUtilImpl` which reads `SecurityContextHolder`
- Same pattern applied in Phase 10 (JwtService) and Phase 20 (RefreshTokenService)

**Lesson:** Any class that accesses platform infrastructure (SecurityContext, Redis, file system) should be behind an interface so tests can mock it cleanly.

### @MockitoSettings(strictness = LENIENT)
`@BeforeEach` stubs `isAdmin()` and `getCurrentUser()` for ALL tests. Not every test calls both. Mockito strict mode (`@ExtendWith(MockitoExtension.class)` default) flags this as `UnnecessaryStubbingException`.

`LENIENT` disables the "unnecessary stubbing" check. Use it **only** when shared setup stubs are genuinely shared across most tests. Don't use it to suppress real bugs.

### MapStruct Nested Path Mapping
```java
@Mapping(source = "owner.username", target = "ownedBy")
TodoResponse toResponse(Todo todo);
```
MapStruct reads `todo.getOwner().getUsername()` and assigns it to `response.ownedBy`.
The traversal is **null-safe**: if `owner` is null (seed todos), `ownedBy` is set to null — not a NullPointerException.

---

## Files Changed

### New Files
- `src/main/resources/db/migration/V6__add_owner_to_todos.sql`
- `src/main/java/com/learnjava/todo/security/SecurityUtil.java` (interface)
- `src/main/java/com/learnjava/todo/security/SecurityUtilImpl.java` (implementation)

### Modified Files
- `src/main/java/com/learnjava/todo/model/Todo.java` — added `@ManyToOne User owner`
- `src/main/java/com/learnjava/todo/repository/TodoRepository.java` — added `findByIdAndOwner()`
- `src/main/java/com/learnjava/todo/service/TodoSpecification.java` — added `hasOwner()` + two-arg `fromFilter()`
- `src/main/java/com/learnjava/todo/service/impl/TodoServiceImpl.java` — all methods now ownership-aware
- `src/main/java/com/learnjava/todo/dto/response/TodoResponse.java` — added `ownedBy` + `@JsonInclude(NON_NULL)`
- `src/main/java/com/learnjava/todo/service/TodoMapper.java` — `owner.username → ownedBy` mapping
- `src/test/java/.../TodoServiceImplTest.java` — mock SecurityUtil, updated all test method stubs
- `src/test/java/.../TodoCacheTest.java` — mock SecurityUtil, updated all cache test stubs

---

## Test Results
```
TodoServiceImplTest      → 11 tests  ✅  (1 new: deleteTodo_admin)
TodoCacheTest            →  5 tests  ✅
TodoMapperTest           →  6 tests  ✅
TodoHealthIndicatorTest  →  3 tests  ✅
RedisCacheConfigTest     →  3 tests  ✅
AuthControllerRefreshTest →  6 tests  ✅
RefreshTokenServiceImplTest → 6 tests ✅
TodoControllerTest       → 14 tests  ✅
TodoApplicationTest      →  1 test   ✅
AuthIntegrationTest      →  5 tests  SKIPPED (Docker unavailable)
TodoIntegrationTest      → 10 tests  SKIPPED (Docker unavailable)

Total: 70 run, 0 failures, 0 errors, 15 skipped → BUILD SUCCESS
```

---

## Design Decisions

### Why nullable `user_id` instead of NOT NULL?
V3 seed data rows exist before any user is registered. A NOT NULL column would require a default user_id — which forces creating a synthetic "seed" user, or fails the migration.
Nullable FK + service-level guarantee (all API-created todos have an owner) is cleaner for this stage.

### Why 404 and not 403 for unowned resource access?
403 confirms the resource exists. 404 leaks no information. This is the standard REST practice for ownership — clients should not know whether an ID belongs to another user or simply doesn't exist.

### Why ADMIN bypass via `SecurityUtil.isAdmin()` instead of `@PreAuthorize`?
`@PreAuthorize("hasRole('ADMIN')")` blocks the method entirely for non-admins — it can't produce different behaviour based on role. We need the same method to return different data (all todos vs. own todos) based on role. That requires in-method branching, not annotation-based denial.

### Why `FetchType.LAZY` on the `@ManyToOne`?
Every `getTodos()` call loads potentially hundreds of todos. With EAGER, each would JOIN the users table — even when we only display the title. With LAZY, the JOIN only happens when `todo.getOwner()` is called (in the mapper). For the list endpoint, all todos belong to the same user so it's effectively one extra query. For single-todo endpoints, the penalty is one extra query — acceptable.

---

## Interview Questions

1. **What is the difference between `@ManyToOne` and `@OneToMany`?**
   `@ManyToOne` is on the "many" side and owns the FK column (holds `user_id`).
   `@OneToMany` is on the "one" side and represents the collection (no FK column here, mapped by the other side).
   In our model: one User has many Todos. The FK `user_id` lives in the `todos` table → `@ManyToOne` on `Todo`.

2. **What is FetchType.LAZY and why should it be the default for @ManyToOne?**
   LAZY: Hibernate loads the related entity only when you call the getter for it.
   EAGER: Hibernate JOINs the related table on every query — even when you don't need it.
   EAGER can cause N+1 queries and load unnecessary data. LAZY gives you control.

3. **What is an N+1 query problem?**
   Loading 100 todos with EAGER on owner executes: 1 query for todos + 100 queries (one per owner).
   With LAZY + a JOIN FETCH in a specific query, you get: 1 query for todos + their owners.
   N+1 is the #1 JPA performance pitfall.

4. **Why return 404 instead of 403 when a user accesses someone else's todo?**
   403 confirms the resource exists — enabling resource enumeration by an attacker.
   404 reveals nothing: the resource either doesn't exist or you can't see it. This is "security by obscurity" at the HTTP layer — accepted best practice for ownership-based access control.

5. **How does Spring Data generate SQL from `findByIdAndOwner(Long id, User owner)`?**
   Spring Data parses the method name: `findBy` + `Id` (maps to `id` field) + `And` + `Owner` (maps to `owner` field).
   It generates: `SELECT * FROM todos WHERE id = ? AND user_id = ?` (user_id is the FK for the owner field).

6. **Why is SecurityUtil an interface?**
   `SecurityUtil` wraps `SecurityContextHolder` — a platform-level singleton.
   Making it an interface means tests can inject a mock (via JDK proxy, no ByteBuddy).
   On Java 25, ByteBuddy cannot subclass Spring infrastructure classes. Same pattern as `JwtService`.

7. **When should you use `@MockitoSettings(strictness = LENIENT)`?**
   When `@BeforeEach` stubs methods that are genuinely shared across tests but not called by every single test.
   Shared stubs in `@BeforeEach` are a legitimate pattern for common test state.
   Never use LENIENT to suppress warnings about stubs that are genuinely wrong or unnecessary.

---

## Best Practices
- Always use `FetchType.LAZY` for `@ManyToOne` by default — opt in to EAGER only when you know you always need the related entity
- Return 404 (not 403) when a user accesses an unowned resource — prevents resource enumeration
- Add FK columns as nullable in migrations when existing rows can't provide the value
- Put SecurityContext access behind an interface — keeps services clean and testable
- Use `@MockitoSettings(strictness = LENIENT)` only for genuinely shared `@BeforeEach` stubs — not as a blanket silencer

---

## Possible Improvements
- Add `@OneToMany(mappedBy = "owner")` on `User` to navigate in the other direction
- Add per-user todo statistics endpoint: `GET /api/v1/users/me/stats` (count, completed, pending)
- Add `V7__backfill_owner.sql` to assign seed todos to an admin user
- Add Testcontainers integration tests that register a user and verify ownership enforcement end-to-end
- Add `GET /api/v1/users/me` endpoint that returns the current user profile

---

## Exercises
1. Register two users (alice, bob). Log in as alice, create a todo. Log in as bob, try to `GET /api/v1/todos/{alice_todo_id}` — observe 404.
2. Log in as an ADMIN, call `GET /api/v1/todos` — observe ALL todos (including seeded ones without owner).
3. Log in as alice, call `GET /api/v1/todos` — observe ONLY alice's todos.
4. Create a todo as alice. Inspect `redis-cli get "todos::1"` — observe `"ownedBy": "alice"` in the cached JSON.
5. Try `DELETE /api/v1/todos/{bob_todo_id}` as alice — observe 404.
