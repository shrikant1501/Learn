package com.learnjava.todo.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

// Immutable response — use @Builder for clean construction in AuthServiceImpl
@Getter
@Builder
@Schema(description = "Response returned after successful register or login")
public class AuthResponse {

    @Schema(description = "JWT bearer token to use in Authorization header",
            example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;

    @Schema(description = "The authenticated username", example = "john_doe")
    private String username;
}
