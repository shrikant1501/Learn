# Phase 19 — Redis Cache

## Goal
Replace the local, single-node Caffeine cache with Redis — a distributed, out-of-process cache.
The `@Cacheable` / `@CacheEvict` annotations on `TodoServiceImpl` are completely unchanged.
Only the infrastructure (cache backend) swaps, demonstrating Spring's cache abstraction.

Profile strategy:
- Default profile (H2, tests) → Caffeine (fast, no infrastructure)
- local + docker profiles      → Redis (distributed, persistent across restarts)

---

## Concepts Learned

### Redis
- Remote Dictionary Server — an in-memory key/value store, runs as a separate process
- Used as: cache, session store, pub/sub broker, distributed lock, leaderboard
- Standard port: 6379
- Key difference from Caffeine: Redis is OUT-OF-PROCESS — data survives app restarts;
  multiple app instances share the same cache (essential for horizontal scaling)

### Spring Data Redis
- `spring-boot-starter-data-redis` — includes Lettuce (async Redis client), RedisTemplate, RedisCacheManager
- Auto-configures `RedisConnectionFactory` from `spring.data.redis.host` + `spring.data.redis.port`
- Lettuce is the default client (non-blocking, based on Netty) — Jedis is the blocking alternative

### RedisCacheManager
- Spring's `CacheManager` implementation backed by Redis
- Configured via `RedisCacheConfiguration` — an immutable value object (fluent builder)
- Key settings: `entryTtl()`, `serializeKeysWith()`, `serializeValuesWith()`, `disableCachingNullValues()`
- `getCacheNames()` only returns caches AFTER `afterPropertiesSet()` is called (Spring lifecycle)
  → In unit tests, use `getCache(name)` instead to verify cache registration

### Serialization strategies
| Serializer | Format | Notes |
|---|---|---|
| `JdkSerializationRedisSerializer` | Binary | Default; requires Serializable; unreadable in redis-cli |
| `GenericJackson2JsonRedisSerializer` | JSON | Human-readable; stores `@class` type hint; best for debugging |
| `Jackson2JsonRedisSerializer` | JSON | Must specify target class at config time; not generic |
We use `GenericJackson2JsonRedisSerializer` — readable in redis-cli, survives class moves.

### @Profile
- `@Profile("default")` — active when NO profile is explicitly set (tests, plain java -jar)
- `@Profile({"local","docker"})` — active when SPRING_PROFILES_ACTIVE is local OR docker
- Spring only instantiates `@Configuration` classes whose `@Profile` matches the active profile
- This is the idiomatic way to swap infrastructure beans (DB, cache, messaging) per environment

### spring.cache.type
- `spring.cache.type=caffeine` — forces Caffeine even when Redis starter is on classpath
- Without this, Spring Boot auto-detects Redis starter and tries to use it → fails if no Redis running
- This explicit override in `application.properties` makes the default profile safe for offline dev/CI

---

## Files Changed

### New Files
- `src/main/java/com/learnjava/todo/config/RedisCacheConfig.java`
- `src/test/java/com/learnjava/todo/config/RedisCacheConfigTest.java`

### Modified Files
- `pom.xml` — added `spring-boot-starter-data-redis`
- `docker-compose.yml` — added `redis:7-alpine` service + `depends_on` in app service
- `application.properties` — added `spring.cache.type=caffeine`, bumped version to 0.19.0
- `application-local.properties` — added Redis host=localhost, `spring.cache.type=redis`
- `application-docker.properties` — added Redis host=redis, `spring.cache.type=redis`
- `config/CacheConfig.java` — added `@Profile("default")` to scope it to default profile only

---

## Design Decisions

### Why @Profile("default") on CacheConfig and @Profile({"local","docker"}) on RedisCacheConfig?
Spring only allows one `CacheManager` bean. If both config classes were active simultaneously,
Spring would find two `CacheManager` beans and fail with "expected single matching bean".
`@Profile` ensures only ONE config class is loaded per environment.

### Why GenericJackson2JsonRedisSerializer over the default JdkSerializationRedisSerializer?
1. Human-readable: `redis-cli get "todos::1"` returns readable JSON
2. Debuggable: you can inspect cache state without a Java deserializer
3. More robust: doesn't break when you rename a class (JDK serializer embeds the fully-qualified class name)
4. No Serializable required on DTOs

### Why disableCachingNullValues()?
Our service returns `Optional<T>`, so nulls never reach the cache layer.
Disabling null caching avoids a `GenericJackson2JsonRedisSerializer` issue: null has no `@class`
type hint in JSON, causing deserialization failure on cache reads.

### Why Lettuce over Jedis?
Lettuce is non-blocking (Netty-based) — better for reactive apps and high-concurrency scenarios.
Jedis uses one connection per thread (blocking). Spring Boot defaults to Lettuce.
For this project either works — we use the default (Lettuce) with no extra config.

---

## Test Results
```
TodoHealthIndicatorTest  →  3 tests  ✅
RedisCacheConfigTest     →  3 tests  ✅
TodoControllerTest       → 14 tests  ✅
TodoCacheTest            →  5 tests  ✅  (Caffeine, default profile)
TodoMapperTest           →  6 tests  ✅
TodoServiceImplTest      → 10 tests  ✅
TodoApplicationTest      →  1 test   ✅
AuthIntegrationTest      →  5 tests  SKIPPED (Docker unavailable)
TodoIntegrationTest      → 10 tests  SKIPPED (Docker unavailable)

Total: 57 run, 0 failures, 0 errors, 15 skipped → BUILD SUCCESS
```

### Gotcha: RedisCacheManager.getCacheNames() in unit tests
`getCacheNames()` only returns caches after `afterPropertiesSet()` is called by Spring's lifecycle.
When constructing the manager manually in a unit test (no Spring context), this returns empty.
**Fix:** Assert via `getCache(name)` — which is the actual runtime code path Spring uses.

---

## Interview Questions

1. **What is the difference between Caffeine and Redis as cache backends?**
   Caffeine: in-process, JVM heap, lost on restart, only one instance can use it.
   Redis: out-of-process, shared across multiple app instances, survives restarts, has TTL persistence.

2. **What does @Profile("default") mean in Spring?**
   It activates the bean/config only when no explicit Spring profile is set.
   "default" is Spring's reserved name for the fallback profile.

3. **Why do we need two CacheConfig classes instead of one?**
   Each environment needs a different CacheManager implementation.
   Spring only allows one CacheManager bean — @Profile ensures only one is loaded per environment.

4. **What is Lettuce and why does Spring Boot use it?**
   Lettuce is a non-blocking, async Redis client built on Netty.
   Spring Boot defaults to it because it handles concurrent requests more efficiently than Jedis (thread-per-connection).

5. **Why use GenericJackson2JsonRedisSerializer for cache values?**
   It serializes to human-readable JSON with a @class type hint for safe deserialization.
   The alternative (JDK binary serialization) is unreadable and breaks on class renames.

6. **What is TTL and why do we set it on the cache?**
   TTL (Time To Live) — the duration after which a cache entry is automatically deleted.
   Without TTL, stale data lives forever. With TTL, even if @CacheEvict fails, data self-heals.

7. **How do you verify Redis is working in production?**
   Check /actuator/health — Spring Boot auto-adds a Redis health indicator when the Redis starter is present.
   It shows "redis: UP" with the Redis server version.

---

## Best Practices
- Always set a TTL — unbounded caches grow forever and cause OOM or stale data issues
- Use JSON serialization for Redis — binary serialization is fragile across deployments
- `disableCachingNullValues()` to avoid null serialization edge cases
- Use `@Profile` to swap infrastructure beans — never put if/else logic inside @Bean methods
- Use `spring.cache.type=<type>` to be explicit — don't rely on auto-detection in multi-starter projects
- Add Redis health indicator monitoring — Spring Boot auto-adds it with the Redis starter

## Possible Improvements
- Add per-cache TTL (e.g., shorter TTL for session data, longer for reference data)
- Add Redis connection pool config (`spring.data.redis.lettuce.pool.*`)
- Add `RedisTemplate<String, Object>` bean for direct Redis operations (Phase 20 — Refresh Tokens)
- Add Testcontainers-based Redis integration test (`@ServiceConnection` with `RedisContainer`)

---

## Exercises
1. Start Redis with `docker compose up redis -d` and run the app with `local` profile — verify `/actuator/health` shows `redis: UP`
2. Use `redis-cli keys *` to see what keys are stored after calling `GET /api/v1/todos/1`
3. Use `redis-cli get "todos::1"` to inspect the raw JSON stored in Redis
4. Call `PUT /api/v1/todos/1` and then `redis-cli keys *` again — verify the evicted key is gone
5. Restart the app (without `docker compose down`) and call `GET /api/v1/todos/1` — the Redis key is still there (survives restart)
