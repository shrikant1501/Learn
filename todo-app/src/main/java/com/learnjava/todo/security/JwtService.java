package com.learnjava.todo.security;

import org.springframework.security.core.userdetails.UserDetails;

// Extracting JwtService as an interface serves two purposes:
// 1. Clean architecture: JwtAuthenticationFilter depends on an abstraction, not an implementation
// 2. Testability: Mockito can mock interfaces without ByteBuddy subclassing — no Java 25 issues
public interface JwtService {

    // Generates a signed JWT token for the given user
    String generateToken(UserDetails userDetails);

    // Extracts the username (subject claim) from a token
    String extractUsername(String token);

    // Returns true if the token is valid for the given user and not expired
    boolean isTokenValid(String token, UserDetails userDetails);
}
