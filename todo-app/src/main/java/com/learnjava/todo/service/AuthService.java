package com.learnjava.todo.service;

import com.learnjava.todo.dto.auth.AuthResponse;
import com.learnjava.todo.dto.auth.LoginRequest;
import com.learnjava.todo.dto.auth.RegisterRequest;

// AuthService is the contract for all authentication operations.
// Keeping an interface here follows the same pattern as TodoService:
// the controller depends on the abstraction, not the implementation.
public interface AuthService {

    // Registers a new user: validates uniqueness, hashes password, saves, returns token.
    AuthResponse register(RegisterRequest request);

    // Authenticates an existing user: verifies credentials, returns token.
    AuthResponse login(LoginRequest request);
}
