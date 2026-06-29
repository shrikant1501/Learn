package com.learnjava.todo.service.impl;

import com.learnjava.todo.dto.auth.AuthResponse;
import com.learnjava.todo.dto.auth.LoginRequest;
import com.learnjava.todo.dto.auth.RefreshTokenRequest;
import com.learnjava.todo.dto.auth.RegisterRequest;
import com.learnjava.todo.exception.UsernameAlreadyExistsException;
import com.learnjava.todo.model.User;
import com.learnjava.todo.repository.UserRepository;
import com.learnjava.todo.security.JwtService;
import com.learnjava.todo.security.RefreshTokenService;
import com.learnjava.todo.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    // AuthenticationManager is wired in from SecurityConfig.
    // Calling manager.authenticate() triggers the full Spring Security auth flow:
    //   DaoAuthenticationProvider → UserDetailsService → PasswordEncoder.matches()
    // If credentials are wrong, it throws BadCredentialsException (which becomes 401).
    private final AuthenticationManager authenticationManager;

    // UserDetailsService is used in refresh() to reload the User entity by username.
    // We inject the bean defined in SecurityConfig (the lambda that calls userRepository).
    private final UserDetailsService userDetailsService;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.debug("Registering new user: {}", request.getUsername());

        // Check for duplicate username before attempting to save.
        // Throwing a domain exception here produces a clean 409 response
        // rather than a DataIntegrityViolationException from the unique constraint.
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new UsernameAlreadyExistsException(request.getUsername());
        }

        // Hash the plain-text password — we never store raw passwords
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        userRepository.save(user);
        log.debug("User '{}' registered successfully", user.getUsername());

        // Token rotation from the first call: issue both access + refresh tokens.
        String accessToken = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user.getUsername());

        return AuthResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .username(user.getUsername())
                .build();
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        log.debug("Login attempt for user: {}", request.getUsername());

        // This single call does everything:
        //   1. Loads user from DB via UserDetailsService
        //   2. Checks the password with BCrypt via PasswordEncoder
        //   3. Throws BadCredentialsException if either step fails
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        // If we reach here, authentication succeeded. Load the user to generate the token.
        User user = (User) userDetailsService.loadUserByUsername(request.getUsername());

        // Issue both access + refresh tokens on every successful login.
        // If the user had an existing refresh token in Redis, it stays there until TTL.
        // Multiple sessions are allowed (multiple refresh tokens per user is fine here).
        String accessToken = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user.getUsername());

        log.debug("User '{}' logged in successfully", user.getUsername());

        return AuthResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .username(user.getUsername())
                .build();
    }

    @Override
    public AuthResponse refresh(RefreshTokenRequest request) {
        String oldRefreshToken = request.getRefreshToken();

        // Step 1: Validate — throws InvalidRefreshTokenException if not found in Redis.
        // This covers: expired tokens, already-rotated tokens, and fabricated tokens.
        String username = refreshTokenService.validateRefreshToken(oldRefreshToken);

        // Step 2: Delete the old token — token rotation, makes it one-time-use.
        // If an attacker steals and uses this token, the next legitimate refresh
        // call will find the key gone and force re-login.
        refreshTokenService.deleteRefreshToken(oldRefreshToken);

        // Step 3: Load user to generate a valid JWT (needs authorities, username)
        User user = (User) userDetailsService.loadUserByUsername(username);

        // Step 4: Issue new token pair
        String newAccessToken = jwtService.generateToken(user);
        String newRefreshToken = refreshTokenService.createRefreshToken(username);

        log.debug("Tokens rotated for user '{}'", username);

        return AuthResponse.builder()
                .token(newAccessToken)
                .refreshToken(newRefreshToken)
                .username(username)
                .build();
    }

    @Override
    public void logout(RefreshTokenRequest request) {
        // Delete the refresh token from Redis.
        // validateRefreshToken is NOT called first intentionally:
        //   - If the token is already gone (double-logout), deleteRefreshToken is a no-op.
        //   - We don't want to return 401 on logout — it's idempotent by design.
        //   - The client considers itself logged out regardless.
        refreshTokenService.deleteRefreshToken(request.getRefreshToken());
        log.debug("Logout: refresh token deleted");
    }
}
