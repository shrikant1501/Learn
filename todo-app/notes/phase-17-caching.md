# Phase 17: Caching with Spring Cache + Caffeine

## Goal
Add an in-memory cache (Caffeine) in front of `getTodoById` using Spring's
cache abstraction. Evict stale entries on `updateTodo` and `deleteTodo`.
Prove the cache works with a dedicated `@SpringBootTest` cache test.

## Test Results
```
TodoCacheTest        :  5 tests PASS  (NEW — proves @Cacheable + @CacheEvict work)
TodoControllerTest   : 14 tests PASS
TodoMapperTest       :  6 tests PASS
TodoServiceImplTest  : 10 tests PASS
TodoApplicationTest  :  1 test  PASS
Integration tests    : 15 tests SKIP  (Docker unavailable)
─────────────────────────────────────────────────────
Total: 51, Failures: 0, Errors: 0, Skipped: 15
BUILD SUCCESS
```

## Files Added
| File | Purpose |
|------|---------|
| `config/CacheConfig.java` | `@EnableCaching` + `CaffeineCacheManager` bean with TTL/size policy |
| `service/TodoCacheTest.java` | 5 tests proving cache hit, miss, and eviction behaviour |

## Files Modified
| File | Change |
|------|--------|
| `pom.xml` | Added `spring-boot-starter-cache` + `caffeine` (version from BOM) |
| `service/impl/TodoServiceImpl.java` | `@Cacheable` on `getTodoById`; `@CacheEvict` on `updateTodo` + `deleteTodo` |
| `application.properties` | Added `logging.level.org.springframework.cache=TRACE` |

## Key Concepts

### The cache abstraction layers
```
Your code          →  @Cacheable / @CacheEvict / @CachePut
Spring abstraction →  CacheManager / Cache interfaces
Caffeine           →  actual in-memory storage, W-TinyLFU eviction
```
Spring's cache abstraction means you can swap Caffeine for Redis (distributed cache)
later by just changing the CacheManager bean — @Cacheable annotations stay the same.

### @Cacheable execution flow
```
Call getTodoById(1L)
         ↓
CacheInterceptor checks cache "todos" for key 1
         ↓
    Cache HIT?   → return cached value (method body NEVER runs)
    Cache MISS?  → call method body → store result → return result
```

### @CacheEvict execution flow
```
Call updateTodo(1L, request)
         ↓
Method body runs → updates DB
         ↓
CacheInterceptor removes key 1 from cache "todos"
         ↓
Next getTodoById(1L) → cache MISS → fresh DB load
```

### Cache configuration choices explained
```java
Caffeine.newBuilder()
    .maximumSize(500)              // LRU eviction at 500 entries
    .expireAfterWrite(10, MINUTES) // TTL: fresh within 10 minutes
```

| Option | Behaviour | Use when |
|--------|-----------|----------|
| `expireAfterWrite` | Entry expires N min after storage | Need predictable freshness |
| `expireAfterAccess` | Entry expires N min after last read | Hot entries stay cached longer |
| `maximumSize` | Evict LRU entry when cache is full | Prevent unbounded memory use |

### Why cache Optional.empty() (not-found results)?
If `getTodoById(999L)` always returns empty, every request would hit the DB.
By caching `Optional.empty()`, the second call for a non-existent id is a cache hit.
This prevents "cache miss storms" for non-existent ids — useful when clients retry.

### Why NOT cache `getTodos` (the paginated list)?
The cache key would need to encode every combination of:
- filter.completed (true/false/null)
- filter.search (any string)
- pageable.page, pageable.size, pageable.sort

This creates an explosion of unique keys, each with a short useful lifetime.
Lists are hard to keep consistent — any todo change invalidates many list pages.
Single-entity caching (`getTodoById`) has bounded key space, high hit rates, and
trivially correct invalidation (one key per entity).

### Why must @SpringBootTest be used to test @Cacheable?
`@Cacheable` works via Spring AOP — a proxy wraps the bean and intercepts calls.
```
Pure Mockito test:
  @InjectMocks creates a plain Java object — NO proxy, NO caching
  @Cacheable annotation is completely invisible
  todoService.getTodoById(1L) always calls the real method

@SpringBootTest test:
  TodoServiceImpl is wrapped in a CGLIB proxy by Spring
  Proxy intercepts getTodoById() and checks the cache first
  @Cacheable works correctly
```
This is a common interview question: "Why doesn't your @Cacheable test work with Mockito?"

### The @BeforeEach cache clear pattern
```java
var cache = cacheManager.getCache("todos");
if (cache != null) cache.clear();
```
Without clearing between tests, a cache entry from test 1 can make test 2
see 0 DB calls when it expects 1 — causing mysterious, order-dependent failures.
Always clear the cache in @BeforeEach for cache-specific tests.

### TRACE log output (visible during test run)
```
CacheInterceptor: Computed cache key '1' for operation Builder[getTodoById] caches=[todos]
CacheInterceptor: Invalidating cache key [1] for operation Builder[updateTodo] caches=[todos]
```
This is the cache abstraction layer showing its work. Turn off in production.

## Gotcha: updateTodo calls findById internally
The test `updateTodo_evictsCacheEntry_nextGetReloadsFromDb` verifies `findById` 
is called **3 times**, not 2:
```
1. getTodoById(1L)     → cache MISS → findById called (#1)
2. updateTodo(1L, ...) → internally calls findById to load entity to mutate (#2)
3. getTodoById(1L)     → cache MISS (evicted) → findById called (#3)
```
If @CacheEvict weren't working, call #3 would be a cache HIT and findById
would only be called 2 times total. The stale title would be returned.

## Common Interview Questions

**Q: What is cache invalidation and why is it hard?**
Cache invalidation is the process of removing or updating stale cache entries when
the underlying data changes. It's hard because you must identify ALL paths that
modify data and ensure each one invalidates the relevant cache entries.
Miss one path → stale data is served. Over-invalidate → no caching benefit.

**Q: What is the difference between @Cacheable and @CachePut?**
`@Cacheable`: method is SKIPPED if cache has the key. Cache populated on miss.
`@CachePut`: method ALWAYS runs, then the result REPLACES the cache entry.
Use @Cacheable for reads. Use @CachePut when you need to update the cache
after a write (e.g., `updateTodo` could use @CachePut instead of @CacheEvict
to keep the cache hot instead of forcing a re-read).

**Q: What is Caffeine's W-TinyLFU algorithm?**
W-TinyLFU (Window Tiny Least Frequently Used) combines a frequency sketch with
a recency component. It provides near-optimal cache hit rates by evicting entries
that are both infrequently AND least recently used. Better than simple LRU for
most real-world access patterns where hot entries are accessed repeatedly.

**Q: When would you replace Caffeine with Redis?**
When you run multiple application instances (horizontal scaling).
Caffeine is per-JVM — a cache entry updated in instance A is not visible to instance B.
Redis is a distributed cache — all instances share the same cache. The switch from
Caffeine to Redis requires only changing the CacheManager bean, not the annotations.

**Q: What is `beforeInvocation = true` on @CacheEvict and when would you use it?**
Default (`false`): evict AFTER the method runs successfully.
  Pro: if the method throws, the cache is preserved (still valid).
`true`: evict BEFORE the method runs.
  Use when: you want to guarantee a fresh read even if the write fails.
  Risk: if the write fails, the cache is empty — next read hits the DB anyway.

## Exercises
1. Add a log statement to `getTodoById` that says "CACHE MISS". Run the app, call
   the endpoint twice. What do you see in the logs on the first vs second call?

2. Change `expireAfterWrite` to 10 **seconds** (not minutes). Call `getTodoById(1)`
   in Swagger, wait 11 seconds, call it again. Does the log show a cache miss?

3. What would happen if you added `@CacheEvict(allEntries = true)` to `createTodo`?
   Would it be correct? Would it be efficient? Why or why not?

4. Replace `@CacheEvict` on `updateTodo` with `@CachePut`. What changes?
   What is the advantage? What is the risk? Update the test to match.

5. The current `TodoServiceImplTest` (Mockito-only) tests `getTodoById` normally.
   Does it test cache behaviour? Why not? What does it test instead?
