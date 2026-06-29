package com.learnjava.todo.security;

import com.learnjava.todo.exception.InvalidRefreshTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pure unit test — no Spring context, no Redis server.
//
// We mock RedisOperations<String,String> (an interface) not StringRedisTemplate (a class).
// Mockito mocks interfaces via JDK dynamic proxy — no ByteBuddy subclassing needed.
// This avoids the "Could not modify all classes" error on Java 25 where ByteBuddy
// cannot instrument Spring infrastructure classes due to module access restrictions.
// Same pattern used in Phase 10 for JwtService.
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    // Mock the RedisOperations interface — this is what our service field is typed as.
    // Spring wires StringRedisTemplate here at runtime; tests wire this mock instead.
    @Mock
    private RedisOperations<String, String> redisTemplate;

    // ValueOperations is what opsForValue() returns — mock it as the interface it is.
    @Mock
    private ValueOperations<String, String> valueOperations;

    private RefreshTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        // Construct the service — opsForValue() stubbing is per-test to avoid
        // UnnecessaryStubbingException when deleteRefreshToken() doesn't call opsForValue().
        service = new RefreshTokenServiceImpl(redisTemplate, 604800000L);
    }

    // -----------------------------------------------------------------------
    // createRefreshToken
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("createRefreshToken: returns a non-blank UUID-format token")
    void createRefreshToken_returnsNonBlankToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String token = service.createRefreshToken("alice");

        assertThat(token).isNotBlank();
        // UUID is 36 characters: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        assertThat(token).hasSize(36);
    }

    @Test
    @DisplayName("createRefreshToken: stores key with 'refresh:' prefix in Redis with TTL")
    void createRefreshToken_storesInRedisWithPrefix() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String token = service.createRefreshToken("alice");

        // Verify opsForValue().set(key, username, ttl) was called with correct prefix
        verify(valueOperations).set(
                eq("refresh:" + token),  // key must use the prefix
                eq("alice"),             // value is the username
                eq(Duration.ofDays(7))   // TTL — 604800000ms = 7 days
        );
    }

    @Test
    @DisplayName("createRefreshToken: two calls produce different tokens")
    void createRefreshToken_uniqueTokensOnEachCall() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String token1 = service.createRefreshToken("alice");
        String token2 = service.createRefreshToken("alice");

        // UUID.randomUUID() is cryptographically random — collision probability is negligible
        assertThat(token1).isNotEqualTo(token2);
    }

    // -----------------------------------------------------------------------
    // validateRefreshToken
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("validateRefreshToken: returns username when token exists in Redis")
    void validateRefreshToken_returnsUsernameForValidToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String token = "some-valid-uuid";
        when(valueOperations.get("refresh:" + token)).thenReturn("alice");

        String username = service.validateRefreshToken(token);

        assertThat(username).isEqualTo("alice");
    }

    @Test
    @DisplayName("validateRefreshToken: throws InvalidRefreshTokenException when token not in Redis")
    void validateRefreshToken_throwsForMissingToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String token = "nonexistent-token";
        when(valueOperations.get("refresh:" + token)).thenReturn(null);

        assertThatThrownBy(() -> service.validateRefreshToken(token))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("invalid or has expired");
    }

    // -----------------------------------------------------------------------
    // deleteRefreshToken
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("deleteRefreshToken: calls redisTemplate.delete with correct prefixed key")
    void deleteRefreshToken_deletesCorrectKey() {
        String token = "token-to-delete";

        service.deleteRefreshToken(token);

        // Verify delete was called with the correct prefixed key
        verify(redisTemplate).delete("refresh:" + token);
    }
}
