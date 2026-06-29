package com.learnjava.todo.service.impl;

import com.learnjava.todo.dto.auth.AuthResponse;
import com.learnjava.todo.dto.auth.LoginRequest;
import com.learnjava.todo.dto.auth.RegisterRequest;
import com.learnjava.todo.exception.UsernameAlreadyExistsException;
import com.learnjava.todo.model.User;
import com.learnjava.todo.repository.UserRepository;
import com.learnjava.todo.security.JwtService;
import com.learnjava.todo.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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

    // AuthenticationManager is wired in from SecurityConfig.
    // Calling manager.authenticate() triggers the full Spring Security auth flow:
    //   DaoAuthenticationProvider → UserDetailsService → PasswordEncoder.matches()
    // If credentials are wrong, it throws BadCredentialsException (which becomes 401).
    private final AuthenticationManager authenticationManager;

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

        // Generate and return a JWT so the user is immediately logged in after registering
        String token = jwtService.generateToken(user);
        return AuthResponse.builder()
                .token(token)
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
        // We don't need to handle the exception here — Spring Security + our
        // GlobalExceptionHandler (catch-all → 500) or Spring's own 401 response handles it.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        // If we reach here, authentication succeeded. Load the user to generate the token.
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(); // can't be empty — authenticate() above already verified existence

        String token = jwtService.generateToken(user);
        log.debug("User '{}' logged in successfully", user.getUsername());

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .build();
    }
}
