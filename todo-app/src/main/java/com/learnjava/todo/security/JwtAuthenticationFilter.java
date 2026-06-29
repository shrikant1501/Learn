package com.learnjava.todo.security;

import com.learnjava.todo.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// OncePerRequestFilter guarantees this filter runs exactly once per HTTP request,
// even if the request is forwarded or dispatched internally within Spring.
// This is the correct base class for all custom security filters.
//
// WHY UserRepository instead of UserDetailsService?
// The UserDetailsService @Bean is defined INSIDE SecurityConfig. If we inject
// UserDetailsService here, Spring sees:
//   SecurityConfig → JwtAuthenticationFilter → UserDetailsService → SecurityConfig  (cycle!)
// By injecting UserRepository directly we break the cycle entirely — UserRepository
// has no dependency on SecurityConfig or anything in the security chain.
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Step 1: Read the Authorization header
        // Convention: "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
        final String authHeader = request.getHeader("Authorization");

        // Step 2: If there is no Bearer token, skip this filter entirely.
        // Spring's later filters will handle the unauthenticated request (return 401).
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Step 3: Extract the raw token — remove the "Bearer " prefix (7 chars)
        final String jwt = authHeader.substring(7);

        // Step 4: Extract the username from the token.
        final String username;
        try {
            username = jwtService.extractUsername(jwt);
        } catch (Exception e) {
            log.warn("JWT parsing failed: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        // Step 5: Only proceed if we got a username AND the user is not already authenticated.
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Step 6: Load the user from the database directly via the repository.
            UserDetails userDetails = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

            // Step 7: Validate the token against the loaded user.
            if (jwtService.isTokenValid(jwt, userDetails)) {

                // Step 8: Create an authenticated token and register it in the SecurityContext.
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);

                log.debug("Authenticated user '{}' via JWT", username);
            }
        }

        // Step 9: ALWAYS pass control to the next filter in the chain.
        filterChain.doFilter(request, response);
    }
}
