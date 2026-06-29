# Phase 16: RBAC — Roles & Permissions

## Goal
Add Role-Based Access Control: every user has a `USER` or `ADMIN` role.
The DELETE endpoint is restricted to `ADMIN` only via `@PreAuthorize`.
Add `@CreatedBy` / `@LastModifiedBy` auditing so every record tracks who created
and last modified it, in addition to when (Phase 12 timestamps).

## Test Results
```
TodoControllerTest   : 14 tests PASS  (includes 2 updated delete tests + 1 new annotation check)
TodoMapperTest       :  6 tests PASS
TodoServiceImplTest  : 10 tests PASS
TodoApplicationTest  :  1 test  PASS  (Flyway V4+V5 run successfully)
Integration tests    : 15 tests SKIP  (Docker unavailable)
─────────────────────────────────────────────────────
Total: 46, Failures: 0, Errors: 0, Skipped: 15
BUILD SUCCESS
```

## Files Added
| File | Purpose |
|------|---------|
| `model/Role.java` | Enum: `USER` (default), `ADMIN` |
| `db/migration/V4__add_role_to_users.sql` | ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER' |
| `db/migration/V5__add_audit_by_columns.sql` | ALTER TABLE todos/users ADD COLUMN created_by, last_modified_by |

## Files Modified
| File | Change |
|------|--------|
| `model/User.java` | Added `role` field + `@Enumerated(STRING)` + `@Builder.Default` + real `getAuthorities()` |
| `model/Auditable.java` | Added `@CreatedBy createdBy` + `@LastModifiedBy lastModifiedBy` fields |
| `config/JpaConfig.java` | Added `AuditorAware<String>` bean, updated `@EnableJpaAuditing(auditorAwareRef)` |
| `config/SecurityConfig.java` | Added `@EnableMethodSecurity` to activate `@PreAuthorize` |
| `controller/TodoController.java` | Added `@PreAuthorize("hasRole('ADMIN')")` on `deleteTodo()` |
| `controller/TodoControllerTest.java` | Updated delete tests to use `@WithMockUser(roles="ADMIN")` + annotation verification test |

## Key Concepts

### Role-Based Access Control (RBAC) Design
```
User registers → assigned Role.USER by default
Admin is set manually (direct SQL or future admin endpoint)

Role.USER  → ROLE_USER  authority → can: GET, POST, PUT todos
Role.ADMIN → ROLE_ADMIN authority → can: everything + DELETE todos
```

### `@Enumerated(EnumType.STRING)` — always use STRING, never ORDINAL
```
ORDINAL: stores 0, 1, 2...
  Problem: add Role.MODERATOR between USER and ADMIN →
  ADMIN shifts from ordinal 1 to 2 — all existing ADMIN rows silently become MODERATOR!

STRING: stores "USER", "ADMIN"
  Safe: adding new values never affects existing rows — the string is immutable
```

### How `@PreAuthorize` evaluation works
```
HTTP Request arrives
    ↓
JwtAuthenticationFilter runs
    → loads User from DB
    → calls user.getAuthorities() → [SimpleGrantedAuthority("ROLE_USER")]
    → sets SecurityContext.Authentication with those authorities
    ↓
Spring AOP intercepts deleteTodo() call
    → @PreAuthorize("hasRole('ADMIN')") evaluates:
       does SecurityContext have authority "ROLE_ADMIN"? NO
    → throws AccessDeniedException
    ↓
ExceptionTranslationFilter catches it → 403 Forbidden
```

### Why `@EnableMethodSecurity` is critical
Without it, `@PreAuthorize` is completely ignored. No error, no warning.
This is the #1 security misconfiguration bug in Spring Boot apps.
A developer adds `@PreAuthorize`, tests it (without the annotation), sees it "works",
deploys — and the endpoint is wide open.
Always add `@EnableMethodSecurity` when you use method-level security.

### `AuditorAware<String>` — the who to complement the when
```java
// Spring calls this lambda before every INSERT and UPDATE
return () -> {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || "anonymousUser".equals(auth.getPrincipal())) {
        return Optional.empty(); // Flyway migration runs — no auth context
    }
    return Optional.of(auth.getName()); // real user operation → store their username
};
```
`auth.getName()` returns the `username` field from the JWT principal (our `User` entity).

### Why `@Builder.Default` on the role field
```java
@Builder.Default
private Role role = Role.USER;
```
Without `@Builder.Default`, Lombok's builder ignores the field initializer.
`User.builder().username("alice").password("...").build()` would produce `role = null`
(Lombok sets all fields to null/0 unless told otherwise).
With `@Builder.Default`, calling `.build()` without `.role(...)` correctly gives `Role.USER`.

### H2 compatibility: one ALTER per statement
```sql
-- WRONG — works in PostgreSQL, fails in H2:
ALTER TABLE todos ADD COLUMN a VARCHAR, ADD COLUMN b VARCHAR;

-- CORRECT — works in both H2 (MODE=PostgreSQL) and PostgreSQL:
ALTER TABLE todos ADD COLUMN a VARCHAR;
ALTER TABLE todos ADD COLUMN b VARCHAR;
```
Always test your migrations against H2 first.

### Why @WebMvcTest cannot test @PreAuthorize enforcement
`@WebMvcTest` loads only the web layer (controllers + filters).
`@PreAuthorize` is enforced by a Spring AOP proxy that wraps the controller bean.
AOP proxies are created in the full ApplicationContext, not in the web-layer slice.
In `@WebMvcTest`, the controller method is called directly — without the proxy.
Solution: test the annotation is declared (unit), test enforcement in integration tests.

## Role permission table
| Endpoint | USER | ADMIN |
|----------|------|-------|
| GET /api/v1/todos | ✅ | ✅ |
| GET /api/v1/todos/{id} | ✅ | ✅ |
| POST /api/v1/todos | ✅ | ✅ |
| PUT /api/v1/todos/{id} | ✅ | ✅ |
| DELETE /api/v1/todos/{id} | ❌ 403 | ✅ 204 |

## Common Interview Questions

**Q: What is the difference between authentication and authorization?**
Authentication = "Who are you?" — verified by JWT token + user lookup.
Authorization = "What can you do?" — verified by @PreAuthorize + role check.
Authentication comes first; authorization only runs after authentication succeeds.

**Q: What is `@EnableMethodSecurity` and why is it required?**
It enables Spring Security's method-level security AOP infrastructure.
Without it, `@PreAuthorize`, `@PostAuthorize`, and `@Secured` are silently ignored.
Always add it to your `@Configuration` class when using method-level security.

**Q: What is the difference between `hasRole('ADMIN')` and `hasAuthority('ROLE_ADMIN')`?**
They are equivalent. `hasRole()` automatically prepends "ROLE_" to the argument.
`hasRole('ADMIN')` → checks for authority "ROLE_ADMIN".
`hasAuthority('ROLE_ADMIN')` → checks for exactly "ROLE_ADMIN" (no prefix added).
Convention: store authorities with "ROLE_" prefix, use `hasRole()` for readability.

**Q: Why use `@Enumerated(EnumType.STRING)` instead of `ORDINAL`?**
ORDINAL stores integers. Adding a value in the middle of the enum shifts all existing
ordinals → silent data corruption. STRING stores the enum name → safe, readable, stable.
Always use STRING for enums stored in DB columns.

**Q: How does Spring populate @CreatedBy automatically?**
1. You define an `AuditorAware<String>` bean that returns the current username.
2. You annotate `@EnableJpaAuditing(auditorAwareRef = "beanName")`.
3. On every `repository.save()`, Spring Data JPA calls `AuditorAware.getCurrentAuditor()`
   and writes the result to fields annotated with `@CreatedBy` / `@LastModifiedBy`.

## Exercises
1. Register a user via Swagger UI. Check the token with jwt.io — can you see the username claim? What about the role?
2. Try to call `DELETE /api/v1/todos/1` with a USER token in Swagger. What response do you get?
3. Directly update a user's role in H2 console: `UPDATE users SET role = 'ADMIN' WHERE username = 'yourname'`. Get a new JWT (login again — the old JWT has no role claim). Now try the DELETE. What happens?
4. Why do we need to login again after changing the role in the DB? (Hint: where is the role stored after login?)
5. What would you change to also restrict `POST /api/v1/todos` to ADMIN only? Make the change and verify with the existing test infrastructure.
