package com.learnjava.todo.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

// @EnableCaching: the master switch for Spring's cache abstraction.
// Without this annotation, ALL @Cacheable / @CacheEvict / @CachePut annotations
// are completely ignored — no error, no warning, just no caching.
// This is the same pattern as @EnableMethodSecurity for @PreAuthorize.
@Configuration
@EnableCaching
public class CacheConfig {

    // Cache name constant — used in @Cacheable annotations on TodoServiceImpl.
    // Keeping it here (instead of magic strings) means a rename is one change.
    public static final String TODOS_CACHE = "todos";

    // CacheManager is the central bean Spring uses to manage all named caches.
    // We configure a CaffeineCacheManager which uses Caffeine under the hood.
    // Spring Boot auto-detects it when Caffeine is on the classpath — but we
    // define it explicitly to control the eviction policy per cache.
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(TODOS_CACHE);

        // Caffeine.newBuilder() creates the specification for each cache instance.
        //
        // maximumSize(500): evict the least-recently-used entry when the cache
        // exceeds 500 items. Prevents unbounded memory growth. For most apps,
        // 500 cached todos is plenty — adjust based on your data set.
        //
        // expireAfterWrite(10, MINUTES): each cache entry lives for 10 minutes
        // after it was written. After 10 minutes, the next access will miss the
        // cache and reload from the DB. This is the TTL (Time To Live).
        //
        // WHY expireAfterWrite and not expireAfterAccess?
        //   expireAfterAccess: timer resets every time the entry is read.
        //     Pro: hot entries stay cached.
        //     Con: a cached entry could be hours old if it's constantly accessed.
        //   expireAfterWrite: timer starts when the entry is stored, period.
        //     Pro: predictable freshness — entries are never more than 10 min old.
        //     Con: very hot entries expire and reload even though they haven't changed.
        //   For a todo app where data changes occasionally, expireAfterWrite gives
        //   better freshness guarantees. Hot apps often use both together.
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(10, TimeUnit.MINUTES));

        return manager;
    }
}
