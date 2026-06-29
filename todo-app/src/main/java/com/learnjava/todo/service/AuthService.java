package com.learnjava.todo.service;

import com.learnjava.todo.dto.auth.AuthResponse;
import com.learnjava.todo.dto.auth.LoginRequest;
import com.learnjava.todo.dto.auth.RefreshTokenRequest;
import com.learnjava.todo.dto.auth.RegisterRequest;

// AuthService is the contract for all authentication operations.
// Keeping an interface here follows the same pattern as TodoService:
// the controller depends on the abstraction, not the implementation.
public interface AuthService {

    // Registers a new user: validates uniqueness, hashes password, saves, returns token pair.
    AuthResponse register(RegisterRequest request);

    // Authenticates an existing user: verifies credentials, returns token pair.
    AuthResponse login(LoginRequest request);

    // Validates the refresh token, rotates it, and returns a new token pair.
    // Token rotation: old refresh token is deleted from Redis; new one is created.
    AuthResponse refresh(RefreshTokenRequest request);

    // Invalidates the refresh token — deletes it from Redis.
    // After logout the refresh token cannot be used to get new access tokens.
    void logout(RefreshTokenRequest request);
}
