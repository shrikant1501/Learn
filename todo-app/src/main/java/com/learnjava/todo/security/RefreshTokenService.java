package com.learnjava.todo.security;

// Manages the lifecycle of refresh tokens stored in Redis.
// Decoupled from AuthServiceImpl via interface — tests inject a mock.
public interface RefreshTokenService {

    // Creates a new refresh token for the given username, stores it in Redis
    // with a TTL, and returns the token value (a random UUID string).
    String createRefreshToken(String username);

    // Validates the token: returns the username it belongs to.
    // Throws InvalidRefreshTokenException if the token doesn't exist (expired or fake).
    String validateRefreshToken(String token);

    // Deletes the refresh token from Redis, invalidating it.
    // Called on logout or as part of token rotation.
    void deleteRefreshToken(String token);
}
