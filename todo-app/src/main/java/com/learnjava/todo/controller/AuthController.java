package com.learnjava.todo.controller;

import com.learnjava.todo.dto.auth.AuthResponse;
import com.learnjava.todo.dto.auth.LoginRequest;
import com.learnjava.todo.dto.auth.RefreshTokenRequest;
import com.learnjava.todo.dto.auth.RegisterRequest;
import com.learnjava.todo.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User registration, login, token refresh, and logout")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(
            summary = "Register a new user",
            description = "Creates a new user account and returns an access + refresh token pair")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "409", description = "Username already taken")
    })
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(
            summary = "Login with existing credentials",
            description = "Authenticates a user and returns an access + refresh token pair")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "403", description = "Invalid credentials")
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh access token",
            description = "Validates the refresh token, rotates it, and returns a new access + refresh token pair. " +
                    "The old refresh token is immediately invalidated (token rotation). " +
                    "Returns 401 if the token is expired or has already been used.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token pair refreshed successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed — refreshToken is blank"),
            @ApiResponse(responseCode = "401", description = "Refresh token is invalid or expired")
    })
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Logout — invalidate refresh token",
            description = "Deletes the refresh token from Redis, preventing its future use. " +
                    "Idempotent — calling logout with an already-expired token returns 204.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logout successful"),
            @ApiResponse(responseCode = "400", description = "Validation failed — refreshToken is blank")
    })
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        // 204 No Content — no body on logout, standard REST convention for "done, nothing to show"
        return ResponseEntity.noContent().build();
    }
}
