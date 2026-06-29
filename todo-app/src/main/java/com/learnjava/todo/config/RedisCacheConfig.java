package com.learnjava.todo.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

// @Profile({"local","docker"}): this entire @Configuration is only loaded when the
// active Spring profile is 'local' OR 'docker'. In the default (H2/test) profile,
// this class is completely invisible to Spring — CacheConfig.java's CaffeineCacheManager
// is used instead.
//
// WHY split into two config classes instead of one?
// Each profile needs a DIFFERENT CacheManager bean. Spring only allows one CacheManager
// bean by default. @Profile is the cleanest way to activate the correct one.
// Alternative: @ConditionalOnProperty — but @Profile is more explicit and readable.
@Configuration
@EnableCaching
@Profile({"local", "docker"})
public class RedisCacheConfig {

    // RedisConnectionFactory is auto-configured by spring-boot-starter-data-redis.
    // It reads spring.data.redis.host and spring.data.redis.port from properties.
    // Lettuce (async, non-blocking Redis client) is the default — no extra config needed.
    // We inject it here so RedisCacheManager can open connections to Redis.
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        // RedisCacheConfiguration is an IMMUTABLE value object describing how
        // cache entries are stored in Redis. Every method returns a NEW instance
        // with that setting applied — this is the "copy-on-modify" fluent pattern.
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()

                // TTL (Time To Live): Redis will automatically delete entries after
                // 10 minutes. This mirrors Caffeine's expireAfterWrite setting.
                // Even without @CacheEvict, stale data self-heals in at most 10 minutes.
                // In Redis: TTL is enforced server-side — survives app restarts.
                // In Caffeine: TTL is JVM-side — lost on restart (no persistence).
                .entryTtl(Duration.ofMinutes(10))

                // Key serializer: store cache keys as plain UTF-8 strings in Redis.
                // This makes keys human-readable in redis-cli:
                //   e.g.  todos::1   (cache name + "::" + key)
                // StringRedisSerializer is the standard choice for String keys.
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))

                // Value serializer: store cache values as JSON.
                // GenericJackson2JsonRedisSerializer uses Jackson ObjectMapper to
                // serialize/deserialize Java objects to/from JSON bytes.
                // JSON is human-readable, debuggable with redis-cli, and
                // survives class refactoring better than Java's binary serialization.
                //
                // WHY NOT JdkSerializationRedisSerializer (the default)?
                //   - Binary: unreadable in redis-cli
                //   - Class name is baked in: breaks if you rename/move a class
                //   - Requires Serializable on every cached class
                //
                // WHY NOT Jackson2JsonRedisSerializer?
                //   - Must specify the target class at config time — not flexible
                //   - GenericJackson2JsonRedisSerializer stores the @class type hint
                //     in the JSON, so deserialization works for any type
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new GenericJackson2JsonRedisSerializer()))

                // Do NOT cache null values. If a method returns null (e.g., not found),
                // Spring will NOT store a null entry in Redis.
                // This differs from Caffeine where we DO cache Optional.empty() as a
                // sentinel value — with Redis, null caching can cause issues with
                // JSON deserialization (null has no @class type hint).
                // Our service uses Optional<T>, so nulls never reach the cache layer.
                .disableCachingNullValues();

        // RedisCacheManager.builder() wires the config to the connection factory.
        // withCacheConfiguration(name, config) applies the same config to the "todos" cache.
        // You could apply DIFFERENT configs per cache name — e.g., shorter TTL for session data.
        return RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration(CacheConfig.TODOS_CACHE, config)
                .build();
    }
}
