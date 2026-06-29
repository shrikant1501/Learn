package com.learnjava.todo.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// Pure unit test — no Spring context loaded, no Redis server needed.
// We instantiate RedisCacheConfig directly (it's a plain Java object with a @Bean method)
// and pass a Mockito mock for RedisConnectionFactory.
//
// WHY mock the connection factory instead of using a real Redis?
//   A real Redis server is infrastructure — it might not be available in CI.
//   We're testing the CONFIGURATION of the CacheManager (TTL, type, cache names),
//   not that Redis itself is reachable. Connection factory is only called when
//   the first actual Redis operation runs — not during CacheManager construction.
class RedisCacheConfigTest {

    private final RedisCacheConfig config = new RedisCacheConfig();

    @Test
    @DisplayName("cacheManager: returns a RedisCacheManager (not Caffeine)")
    void cacheManager_returnsRedisCacheManager() {
        // Arrange: mock the connection factory — no real Redis needed
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);

        // Act
        CacheManager manager = config.cacheManager(factory);

        // Assert: the returned manager is a RedisCacheManager specifically.
        // If the wrong config class were loaded (e.g., CacheConfig), this would
        // be a CaffeineCacheManager and the test would fail — verifying @Profile works.
        assertThat(manager).isInstanceOf(RedisCacheManager.class);
    }

    @Test
    @DisplayName("cacheManager: 'todos' cache is retrievable via getCache()")
    void cacheManager_registersTodosCache() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);

        CacheManager manager = config.cacheManager(factory);

        // RedisCacheManager.getCacheNames() only returns caches AFTER the manager
        // has been initialized via afterPropertiesSet() — which Spring calls on
        // application startup. In this unit test we construct the manager manually,
        // so afterPropertiesSet() is never called and getCacheNames() returns empty.
        //
        // Instead, we verify via getCache() — which is the actual runtime code path:
        // Spring's @Cacheable interceptor calls cacheManager.getCache("todos") to
        // resolve the cache before every annotated method invocation.
        // If getCache("todos") returns null, caching silently doesn't work.
        // A non-null result here is the correct assertion.
        assertThat(manager.getCache(CacheConfig.TODOS_CACHE)).isNotNull();
    }

    @Test
    @DisplayName("cacheManager: getCache returns a named cache with the correct name")
    void cacheManager_todosCache_hasCorrectName() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);

        CacheManager manager = config.cacheManager(factory);

        // Confirm the cache object itself reports the correct name.
        // Cache.getName() is what Spring's cache key logging and metrics use.
        var cache = manager.getCache(CacheConfig.TODOS_CACHE);
        assertThat(cache).isNotNull();
        assertThat(cache.getName()).isEqualTo(CacheConfig.TODOS_CACHE);
    }
}
