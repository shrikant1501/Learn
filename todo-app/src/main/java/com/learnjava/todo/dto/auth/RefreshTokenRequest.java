package com.learnjava.todo.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

// Request body for POST /auth/refresh and POST /auth/logout.
// The refresh token is an opaque UUID string the client received at login.
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRequest {

    // @NotBlank catches null, empty string, and whitespace-only values.
    // Without this, a missing token would propagate into service code
    // and produce a NullPointerException instead of a clear 400 validation error.
    @NotBlank(message = "Refresh token must not be blank")
    private String refreshToken;
}
