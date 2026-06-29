# Phase 10 — Spring Security + JWT Authentication

## Goal
Protect all Todo endpoints with JWT-based authentication. Any request to `/api/v1/todos/**`
must carry a valid `Authorization: Bearer <token>` header. Public endpoints:
`/api/v1/auth/register`, `/api/v1/auth/login`, `/swagger-ui/**`, `/h2-console/**`.

---

## New Files Created

```
src/main/java/com/learnjava/todo/
├── model/
│   └── User.java                    ← @Entity implementing UserDetails
├── repository/
│   └── UserRepository.java          ← findByUsername(String) derived query
├── dto/auth/
│   ├── RegisterRequest.java         ← { username, password } with @NotBlank @Size
│   ├── LoginRequest.java            ← { username, password }
│   └── AuthResponse.java            ← { token, username }
├── security/
│   ├── JwtService.java              ← interface (generateToken, extractUsername, isTokenValid)
│   ├── JwtServiceImpl.java          ← HMAC-SHA256 implementation using JJWT 0.12.5
│   └── JwtAuthenticationFilter.java ← OncePerRequestFilter; reads Bearer token per request
├── config/
│   └── SecurityConfig.java          ← SecurityFilterChain, BCrypt, AuthManager, stateless
├── service/
│   ├── AuthService.java             ← interface: register + login
│   └── impl/
│       └── AuthServiceImpl.java     ← BCrypt password encoding, AuthManager.authenticate()
├── controller/
│   └── AuthController.java          ← POST /api/v1/auth/register + /login
└── exception/
    └── UsernameAlreadyExistsException.java ← 409 Conflict on duplicate username
```

---

## Key Concepts Learned

### JWT Structure
```
Header.Payload.Signature
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huIn0.SflKxw...
```
- **Header**: algorithm (HS256)
- **Payload**: claims — sub (username), iat (issued at), exp (expiry)
- **Signature**: HMAC of header+payload using the secret key
- Server stores only the secret key, never the token

### Why Stateless?
No HttpSession. Every request carries its own JWT. Any server instance
can verify it → horizontal scaling. Tradeoff: cannot revoke tokens until expiry.

### Security Filter Chain Order
```
Request → JwtAuthenticationFilter → UsernamePasswordAuthFilter → Controller
```
Our filter runs BEFORE Spring's built-in auth filter. If a valid JWT is found,
we set the `SecurityContext` and the user is considered authenticated.

### Circular Dependency Resolution
`SecurityConfig` → `JwtAuthenticationFilter` → `UserDetailsService` → `SecurityConfig` = CYCLE

Fix: `JwtAuthenticationFilter` injects `UserRepository` directly (no circular dep).

### JwtService Interface Pattern
Extracted `JwtService` as an interface so Mockito can mock it via JDK proxy (not ByteBuddy subclassing).
This solves the "Cannot mock this class" error on Java 23+ without inline mocking.

### BCrypt Password Encoding
- Register: `passwordEncoder.encode(rawPassword)` → stores ~60-char hash
- Login: `authManager.authenticate()` → internally calls `passwordEncoder.matches()`
- Never store or compare raw passwords

---

## Modified Files

| File | Change |
|------|--------|
| `pom.xml` | Added `spring-boot-starter-security`, `jjwt-api/impl/jackson 0.12.5`, `spring-security-test`, Surefire argLine |
| `application.properties` | Added `jwt.secret` and `jwt.expiration` |
| `OpenApiConfig.java` | Added `SecurityScheme` (bearerAuth) + `SecurityRequirement` for Swagger Authorize button |
| `GlobalExceptionHandler.java` | Added `UsernameAlreadyExistsException` → 409 handler |
| `TodoControllerTest.java` | Added `@WithMockUser`, `@TestPropertySource` (jwt.*), `@MockBean JwtService + UserRepository`, `.with(csrf())` on POST/PUT/DELETE |

---

## Test Changes Explained

### `@WithMockUser`
Injects a fake authenticated user into the SecurityContext for the test.
Does NOT hit the database. Bypasses JWT filter entirely.
Without it → all requests return 401.

### `@MockBean JwtService`, `@MockBean UserRepository`
`@WebMvcTest` loads the security filter chain which includes `JwtAuthenticationFilter`.
That filter needs `JwtService` and `UserRepository` — both unavailable in `@WebMvcTest`.
`@MockBean` provides stub versions so the context assembles.

### `.with(csrf())`
`@WebMvcTest` + Spring Security enables CSRF protection in tests by default.
Our `SecurityConfig` disables CSRF for production (JWT APIs don't need it).
But in test slice, CSRF is re-enabled by `MockMvcSecurityConfiguration`.
`.with(csrf())` injects a valid CSRF token so POST/PUT/DELETE return expected codes, not 403.

---

## Authentication Flow

### Register
```
POST /api/v1/auth/register { "username": "john", "password": "secret123" }
  → AuthController.register()
  → AuthServiceImpl.register()
      → check uniqueness (409 if taken)
      → BCrypt.encode(password)
      → userRepository.save(user)
      → JwtServiceImpl.generateToken(user)
  → 200 { "token": "eyJ...", "username": "john" }
```

### Login
```
POST /api/v1/auth/login { "username": "john", "password": "secret123" }
  → AuthController.login()
  → AuthServiceImpl.login()
      → authManager.authenticate(UsernamePasswordAuthenticationToken)
          → DaoAuthenticationProvider
              → UserDetailsService.loadUserByUsername("john")
              → BCrypt.matches("secret123", storedHash)
      → JwtServiceImpl.generateToken(user)
  → 200 { "token": "eyJ...", "username": "john" }
```

### Protected Request
```
GET /api/v1/todos  Authorization: Bearer eyJ...
  → JwtAuthenticationFilter
      → extract "Bearer " prefix
      → JwtServiceImpl.extractUsername(token)
      → userRepository.findByUsername(username)
      → JwtServiceImpl.isTokenValid(token, user)
      → SecurityContextHolder.setAuthentication(...)
  → passes through to TodoController
  → 200 [...]
```

---

## application.properties JWT Config
```properties
jwt.secret=todo-app-super-secret-key-for-learning-phase-10-2024
jwt.expiration=86400000   # 24 hours in ms
```
**Production rule**: `jwt.secret` must be an environment variable or from a secrets manager.
Never commit real secrets to git.

---

## Common Interview Questions

**Q: What is the difference between authentication and authorization?**
Authentication = "Who are you?" (identity verification via login)
Authorization = "What can you do?" (permission checks — Phase 18 adds RBAC)

**Q: Why is JWT stateless?**
The server stores only the signing key. All user info is in the token payload.
Any server instance can verify any token without shared state.

**Q: What is the risk of long-lived JWTs?**
If a token is stolen, it cannot be revoked until it expires.
Mitigation: short expiry (15 min) + refresh token pattern.

**Q: Why BCrypt and not MD5/SHA-256?**
BCrypt is deliberately slow (configurable work factor). MD5/SHA are fast → brute-force friendly.
BCrypt also includes a salt automatically → no rainbow table attacks.

**Q: What is a circular dependency and how did we solve it here?**
SecurityConfig needed JwtAuthFilter which needed UserDetailsService which was defined IN SecurityConfig.
Solution: JwtAuthFilter depends on UserRepository directly, breaking the cycle.

**Q: What is `OncePerRequestFilter`?**
A Spring filter base class that guarantees exactly one execution per request,
even if the request is forwarded internally (e.g., to an error handler). Critical for security filters.

---

## Test Results
```
TodoControllerTest    : 13 tests ✅
TodoMapperTest        :  6 tests ✅
TodoServiceImplTest   : 10 tests ✅
TodoApplicationTest   :  1 test  ✅
─────────────────────────────────
Total                 : 30 tests ✅  BUILD SUCCESS
```

---

## Next Phase Preview — Phase 11: Integration Testing with Testcontainers
Instead of `@WebMvcTest` (web slice only) or unit tests,
integration tests start the full Spring context with a real PostgreSQL database
(via Docker container managed by Testcontainers) and test the full request→DB→response flow.
