package com.learnjava.todo.config;

import com.learnjava.todo.repository.UserRepository;
import com.learnjava.todo.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// @EnableWebSecurity activates Spring Security's web support.
// Without this, adding spring-boot-starter-security just enables basic auth on everything.
// With this, we take full control of the filter chain.
//
// @EnableMethodSecurity: activates @PreAuthorize, @PostAuthorize, @Secured on any @Bean.
// prePostEnabled=true (the default) enables the @PreAuthorize / @PostAuthorize annotations.
// Without this annotation, @PreAuthorize is silently ignored — no error, no protection.
// This is the most common security misconfiguration bug in Spring Boot applications.
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserRepository userRepository;

    // -----------------------------------------------------------------------
    // SecurityFilterChain — the core security ruleset
    // -----------------------------------------------------------------------

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Disable CSRF (Cross-Site Request Forgery) protection.
                // CSRF attacks exploit browser session cookies. Since we use JWT in
                // Authorization headers (not cookies), CSRF is not a threat here.
                .csrf(AbstractHttpConfigurer::disable)

                // Define URL authorization rules — ORDER MATTERS.
                // Spring evaluates rules top-to-bottom; first match wins.
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints — no token required
                        .requestMatchers(
                                "/api/v1/auth/**",          // register + login
                                "/h2-console/**",           // H2 browser console (dev only)
                                "/swagger-ui/**",           // Swagger UI SPA assets (JS, CSS)
                                "/swagger-ui.html",         // redirects to /swagger-ui/index.html
                                "/v3/api-docs",             // OpenAPI JSON root
                                "/v3/api-docs/**",          // OpenAPI sub-paths (groups etc.)
                                "/swagger-resources/**",    // SpringDoc internal resources
                                "/webjars/**",              // Swagger UI webjar static files
                                // Actuator: health + info are public — load balancers and
                                // Kubernetes liveness/readiness probes require no credentials.
                                "/actuator/health/**",
                                "/actuator/info"
                        ).permitAll()
                        // Sensitive actuator endpoints require ADMIN role.
                        // /actuator/metrics, /actuator/env, /actuator/loggers etc. reveal
                        // internal configuration and performance data — restrict to admins.
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // Every other request requires a valid JWT
                        .anyRequest().authenticated()
                )

                // Stateless session management — the server never creates an HttpSession.
                // Each request must carry its own JWT. This is the correct setting for REST APIs.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Register our custom DaoAuthenticationProvider.
                // This tells Spring Security HOW to authenticate — using our UserDetailsService
                // (which loads from the DB) and our BCryptPasswordEncoder.
                .authenticationProvider(authenticationProvider())

                // Insert our JWT filter BEFORE Spring's built-in username/password filter.
                // This ensures the SecurityContext is populated from the JWT before any
                // other authentication mechanism runs.
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

                // Allow H2 console to display inside a frame (it uses HTML framesets).
                // Spring Security's default headers block frames (X-Frame-Options: DENY).
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))

                .build();
    }

    // -----------------------------------------------------------------------
    // UserDetailsService — how Spring Security loads a user by username
    // -----------------------------------------------------------------------

    // We define this as a @Bean (not a class implementing the interface) using a lambda.
    // This is clean and avoids creating a separate UserDetailsServiceImpl class.
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + username));
    }

    // -----------------------------------------------------------------------
    // Authentication building blocks
    // -----------------------------------------------------------------------

    // DaoAuthenticationProvider wires together:
    //   - UserDetailsService → loads the user from DB
    //   - PasswordEncoder   → verifies the password hash
    // Spring Security calls this during login to authenticate credentials.
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService());
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    // AuthenticationManager is the top-level entry point for authentication.
    // We expose it as a bean so AuthServiceImpl can call manager.authenticate()
    // during login, which triggers the DaoAuthenticationProvider above.
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    // BCryptPasswordEncoder — the industry-standard password hashing algorithm.
    // Strength factor default (10) means ~100ms per hash — fast enough for users,
    // too slow for brute-force attackers trying millions of passwords.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
