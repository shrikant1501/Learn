package com.learnjava.todo.security;

import com.learnjava.todo.exception.InvalidRefreshTokenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

// Stores refresh tokens in Redis as:
//   key:   "refresh:{tokenValue}"
//   value: "{username}"
//   TTL:   configured via jwt.refresh-token-expiration (milliseconds)
//
// This design means:
//   - Lookup is O(1) — direct key get, no scan needed
//   - Expiry is handled by Redis automatically — no cleanup job needed
//   - Revoking a token = deleting one key
@Slf4j
@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    // Key prefix — makes refresh token keys easy to identify in redis-cli
    // and groups them for potential bulk operations (e.g., flush all tokens).
    private static final String KEY_PREFIX = "refresh:";

    // RedisOperations<String, String> is the interface that StringRedisTemplate implements.
    // Depending on the interface (not the class) lets tests mock it via JDK proxy
    // without ByteBuddy subclassing — the same reason JwtService is an interface.
    // Spring auto-wires StringRedisTemplate here (it implements RedisOperations<String,String>).
    private final RedisOperations<String, String> redisTemplate;

    // TTL in milliseconds read from application.properties (jwt.refresh-token-expiration).
    // Using @Value here (not constructor injection of Duration) because Spring
    // natively converts the String property to long — clean and explicit.
    private final Duration refreshTokenTtl;

    // Spring auto-wires StringRedisTemplate here because it implements RedisOperations<String,String>
    // and is the only bean of that generic type in the context.
    // The interface type in the parameter means Mockito can inject a mock without ByteBuddy.
    public RefreshTokenServiceImpl(
            RedisOperations<String, String> redisTemplate,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpirationMs) {
        this.redisTemplate = redisTemplate;
        // Convert millis to Duration once at construction — no repeated math in hot paths
        this.refreshTokenTtl = Duration.ofMillis(refreshTokenExpirationMs);
    }

    @Override
    public String createRefreshToken(String username) {
        // UUID.randomUUID() produces a cryptographically strong random 128-bit value.
        // This is the industry standard for opaque tokens — unpredictable and unique.
        // Alternatives (JWT, PASETO) are overkill here because we validate statelessly
        // in Redis anyway, not by verifying a signature.
        String token = UUID.randomUUID().toString();
        String key = KEY_PREFIX + token;

        // SET key value EX seconds — stores value with automatic expiry.
        // opsForValue().set(key, value, duration) maps directly to Redis SETEX.
        // Redis deletes the key after TTL — no cron job or cleanup code needed.
        redisTemplate.opsForValue().set(key, username, refreshTokenTtl);
        log.debug("Created refresh token for user '{}' with TTL {}", username, refreshTokenTtl);

        return token;
    }

    @Override
    public String validateRefreshToken(String token) {
        String key = KEY_PREFIX + token;

        // GET key — returns null if the key doesn't exist (expired or never stored).
        // We interpret null as "token invalid" — same response whether it expired
        // or was never valid (no information disclosure about expiry state).
        String username = redisTemplate.opsForValue().get(key);

        if (username == null) {
            log.warn("Refresh token not found in Redis: {}", token);
            throw new InvalidRefreshTokenException();
        }

        return username;
    }

    @Override
    public void deleteRefreshToken(String token) {
        String key = KEY_PREFIX + token;
        Boolean deleted = redisTemplate.delete(key);
        log.debug("Deleted refresh token from Redis, key existed: {}", deleted);
    }
}
