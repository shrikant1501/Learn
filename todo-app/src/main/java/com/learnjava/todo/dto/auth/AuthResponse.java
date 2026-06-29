package com.learnjava.todo.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

// Returned by /auth/register, /auth/login, and /auth/refresh.
// refreshToken is nullable — register/login include it; future
// callers that only want an access token renewal can omit it if desired.
// @JsonInclude(NON_NULL) prevents null fields from appearing in the JSON body.
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response returned after successful register, login, or token refresh")
public class AuthResponse {

    @Schema(description = "Short-lived JWT access token (15 min) — use in Authorization header",
            example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;

    // Opaque random string stored server-side in Redis.
    // Use this to request a new access token via POST /auth/refresh.
    // Lives for 7 days (enforced by Redis TTL).
    @Schema(description = "Long-lived refresh token (7 days) — use to renew the access token",
            example = "550e8400-e29b-41d4-a716-446655440000")
    private String refreshToken;

    @Schema(description = "The authenticated username", example = "john_doe")
    private String username;
}
