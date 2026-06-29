# Phase 20 — Refresh Tokens

## Goal
Replace the single long-lived JWT (24h) with a two-token security model:
- **Access token** (JWT, 15 min) — short-lived, stateless, used in Authorization header
- **Refresh token** (UUID, 7 days) — long-lived, stateful (stored in Redis), used to get new access tokens

New endpoints:
- `POST /auth/refresh` — validates the refresh token, rotates it, returns a new token pair
- `POST /auth/logout`  — deletes the refresh token from Redis (idempotent)

---

## Concepts Learned

### The Two-Token Security Model
| Token | Lifetime | Storage | Purpose |
|---|---|---|---|
| Access (JWT) | 15 min | Client memory | Call API endpoints |
| Refresh (UUID) | 7 days | Redis (server) | Get new access tokens |

- Access tokens are **stateless** — validated by verifying the JWT signature, no DB lookup
- Refresh tokens are **stateful** — validated by checking Redis; can be explicitly revoked
- This solves the JWT revocation problem: stolen access tokens expire in 15 min; stolen refresh tokens are one-time-use (token rotation)

### Token Rotation
On every `/auth/refresh` call:
1. Validate old refresh token in Redis
2. **Delete** old token immediately (one-time-use)
3. Issue new access token + new refresh token
4. Store new refresh token in Redis

Security property: if an attacker steals a refresh token and uses it first, the legitimate user's next refresh call fails and they get forced to re-login. Both sessions are invalidated.

### Redis Key Design
```
Key:   "refresh:{tokenValue}"       (e.g., "refresh:550e8400-e29b-41d4-a716-446655440000")
Value: "{username}"                  (e.g., "alice")
TTL:   7 days (auto-expires — no cleanup job needed)
```
- O(1) lookup: `GET refresh:{token}` → username or null
- `delete()` for rotation and logout
- TTL handles cleanup automatically — even if logout is never called, stale tokens vanish

### UUID as Token Value
`UUID.randomUUID()` produces a 128-bit cryptographically random string.
- Unpredictable: cannot be guessed by brute force (2^122 search space)
- Unique: collision probability is negligible (~10^-18)
- No signature: the server validates by looking it up in Redis, not by verifying math
- JWTs/PASETOs are overkill for refresh tokens — we store state anyway, so opaqueness is fine

### Idempotent Logout
Logout does NOT call `validateRefreshToken()` first:
- If the token is already gone (expired or double-logout), `deleteRefreshToken()` is a no-op
- Returning 401 on logout would be wrong — the client wants to be logged out regardless
- REST best practice: DELETE operations are idempotent by definition

### `RedisOperations<K,V>` as Dependency Type
`RefreshTokenServiceImpl` accepts `RedisOperations<String,String>` in its constructor instead of the concrete `StringRedisTemplate`.
- **Why:** `StringRedisTemplate` is a concrete class with Spring infrastructure internals.
  ByteBuddy (used by Mockito) cannot subclass it on Java 25 due to module restrictions.
  Same issue seen in Phase 10 with `JwtServiceImpl` → solved by extracting `JwtService` interface.
- **Fix:** Depend on `RedisOperations<String,String>` (the interface `StringRedisTemplate` implements).
  Mockito mocks interfaces via JDK dynamic proxy — zero ByteBuddy, zero Java 25 issues.
- Spring still auto-wires `StringRedisTemplate` at runtime (it's the only `RedisOperations<String,String>` bean).

---

## Files Changed

### New Files
- `src/main/java/com/learnjava/todo/security/RefreshTokenService.java`
- `src/main/java/com/learnjava/todo/security/RefreshTokenServiceImpl.java`
- `src/main/java/com/learnjava/todo/exception/InvalidRefreshTokenException.java`
- `src/main/java/com/learnjava/todo/dto/auth/RefreshTokenRequest.java`
- `src/test/java/com/learnjava/todo/security/RefreshTokenServiceImplTest.java`
- `src/test/java/com/learnjava/todo/controller/AuthControllerRefreshTest.java`

### Modified Files
- `src/main/java/com/learnjava/todo/dto/auth/AuthResponse.java` — added `refreshToken` field + `@JsonInclude(NON_NULL)`
- `src/main/java/com/learnjava/todo/service/AuthService.java` — added `refresh()` and `logout()` methods
- `src/main/java/com/learnjava/todo/service/impl/AuthServiceImpl.java` — implemented `refresh()`, `logout()`, updated `login()` and `register()` to return token pair
- `src/main/java/com/learnjava/todo/controller/AuthController.java` — added `/auth/refresh` and `/auth/logout` endpoints
- `src/main/java/com/learnjava/todo/exception/GlobalExceptionHandler.java` — added 401 handler for `InvalidRefreshTokenException`
- `src/main/resources/application.properties` — `jwt.expiration` → 900000ms (15 min), added `jwt.refresh-token-expiration=604800000` (7 days)
- `src/test/resources/application-test.properties` — added `jwt.refresh-token-expiration`
- `src/test/java/.../TodoCacheTest.java` — updated `jwt.expiration` in `@TestPropertySource`
- `src/test/java/.../TodoControllerTest.java` — updated `jwt.expiration` in `@TestPropertySource`
- `pom.xml` — added `--add-opens java.base/java.lang.invoke=ALL-UNNAMED` to surefire argLine

---

## Design Decisions

### Why UUID instead of a JWT for the refresh token?
Refresh tokens are stateful (stored in Redis), so their value is validated by lookup, not by cryptographic verification.
Using JWT for a refresh token would be wasteful — you'd still need Redis to support revocation.
UUID is simpler, smaller, and just as secure when the lookup itself is the validation.

### Why `disableCachingNullValues()` on the Redis cache but allow null in refresh token validation?
Cache layer: `Optional<T>` is used so nulls never reach `@Cacheable` — disabling null caching avoids JSON serialization edge cases.
Refresh token: null from `redisTemplate.opsForValue().get()` is the signal for "token not found" — we handle it explicitly with an exception, not caching.

### Why is `logout()` idempotent (no validation before delete)?
- A "token not found" during logout is a success case: the token is already gone.
- Returning 401 on logout would be surprising to clients and break logout-on-tab-close patterns.
- `RedisOperations.delete()` returns false (not an exception) if the key doesn't exist — safe to call repeatedly.

### Why `@JsonInclude(NON_NULL)` on `AuthResponse`?
`refreshToken` is nullable (hypothetically). Without `NON_NULL`, the JSON would contain `"refreshToken": null` in any response that doesn't set it, which is noise. `NON_NULL` ensures clean JSON: only present fields appear.

---

## Test Results
```
RefreshTokenServiceImplTest  →  6 tests  ✅
AuthControllerRefreshTest    →  6 tests  ✅
TodoHealthIndicatorTest      →  3 tests  ✅
RedisCacheConfigTest         →  3 tests  ✅
TodoControllerTest           → 14 tests  ✅
TodoCacheTest                →  5 tests  ✅  (Caffeine, default profile)
TodoMapperTest               →  6 tests  ✅
TodoServiceImplTest          → 10 tests  ✅
TodoApplicationTest          →  1 test   ✅
AuthIntegrationTest          →  5 tests  SKIPPED (Docker unavailable)
TodoIntegrationTest          → 10 tests  SKIPPED (Docker unavailable)

Total: 69 run, 0 failures, 0 errors, 15 skipped → BUILD SUCCESS
```

---

## Interview Questions

1. **Why use a refresh token at all? Why not just use a long-lived access JWT?**
   Long-lived JWTs cannot be revoked — a stolen token gives attackers access until expiry.
   Short-lived access tokens + revocable refresh tokens give both security and usability:
   the access window for a stolen token is small (15 min), and refresh tokens can be explicitly invalidated.

2. **What is token rotation and why does it help against token theft?**
   On each refresh, the old token is deleted and a new one is issued.
   If an attacker steals and uses the refresh token first, the legitimate user's next refresh fails — triggering re-login.
   Both the attacker and the user are locked out, making the incident detectable.

3. **Why store refresh tokens in Redis instead of the database?**
   Redis excels at ephemeral, time-bounded key/value data: TTL is built-in, lookup is O(1), and writes are fast.
   Storing in a relational DB would add a full table scan or indexed lookup for every token operation.
   Redis is also the right semantic: tokens are transient session data, not business entities.

4. **What does `RedisOperations<K,V>` buy you over `StringRedisTemplate`?**
   It's an interface — Mockito can mock it via JDK dynamic proxy without ByteBuddy subclassing.
   On Java 25, ByteBuddy cannot instrument Spring infrastructure classes due to module restrictions.
   Depending on the interface instead of the class is the same principle behind `JwtService` (Phase 10).

5. **What happens if a client calls `/auth/logout` with an expired token?**
   `deleteRefreshToken()` calls `redisTemplate.delete(key)`. If the key is already gone, it returns false but does NOT throw.
   The logout endpoint returns 204 regardless — idempotent by design.
   This is the correct REST behaviour: a DELETE that finds nothing already deleted is still a success.

6. **How would you implement "logout all devices" (revoke all tokens for a user)?**
   Current design stores `refresh:{tokenValue}` → `{username}`.
   To support "logout all", add a second index: `refresh:user:{username}` as a Redis Set of token values.
   On "logout all", SMEMBERS the set, delete each `refresh:{tokenValue}` key, then delete the set.
   This is the standard Redis secondary-index pattern.

7. **What is the difference between `@NotBlank` and `@NotNull` for the refresh token field?**
   `@NotNull` only rejects null. An empty string `""` would pass `@NotNull`.
   `@NotBlank` rejects null, empty string, AND whitespace-only strings.
   For string fields that must have meaningful content (like tokens, usernames, passwords), always use `@NotBlank`.

---

## Best Practices

- Store only the token hash (SHA-256) in Redis if you want extra security — tokens in transit are more valuable than hashes
- Use HttpOnly cookies for refresh tokens in browser-based apps (not request body) — prevents XSS theft
- Always rotate on refresh — static refresh tokens are equivalent to passwords
- Set a meaningful TTL — never unbounded refresh tokens
- Return the same 401 whether the token is expired OR was never valid — no information disclosure
- Logout is idempotent — never return 4xx for "token already gone"

---

## Possible Improvements

- Add "logout all sessions": store a Redis Set `refresh:user:{username}` as a secondary index
- Hash the token before storing in Redis: `SHA-256(token)` as key, so raw tokens are never at rest
- Use HttpOnly cookie for the refresh token (prevents XSS access from JavaScript)
- Add `/auth/me` endpoint: validates the access token and returns the current user info
- Add Testcontainers-based integration test for the full refresh token flow with a real Redis

---

## Exercises

1. Call `POST /auth/login` in Swagger — observe the response now includes `refreshToken` alongside `token`
2. Use the `refreshToken` to call `POST /auth/refresh` — observe you get a new `token` and a different `refreshToken`
3. Call `POST /auth/refresh` again with the OLD `refreshToken` from step 1 — observe 401 (one-time-use)
4. Call `POST /auth/logout` with the current refresh token — then try to refresh again and observe 401
5. Call `POST /auth/logout` twice with the same token — observe both return 204 (idempotent)
6. Start Redis with `docker compose up redis -d`, run the app with `local` profile, and use `redis-cli keys "refresh:*"` to inspect active refresh tokens
