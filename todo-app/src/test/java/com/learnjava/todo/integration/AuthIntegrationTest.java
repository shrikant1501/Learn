package com.learnjava.todo.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnjava.todo.dto.auth.LoginRequest;
import com.learnjava.todo.dto.auth.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ============================================================================
// AuthIntegrationTest — end-to-end tests for /api/v1/auth/**
//
// What makes this an INTEGRATION test (not a unit test):
//   - Full Spring ApplicationContext is loaded (no mocks for service/repository)
//   - Real PostgreSQL runs inside a Docker container (via Testcontainers)
//   - Flyway migrations execute: users table is created by V2__create_users.sql
//   - BCrypt password encoding runs for real
//   - JWT is generated and returned as a real token string
//   - The entire request→security→controller→service→repository→DB→response chain executes
//
// @DirtiesContext: after this test class finishes, Spring discards and recreates
// the ApplicationContext for the next test class. We need this because auth tests
// INSERT users into the DB. Flyway's V3 seed data only covers todos, not users.
// Without @DirtiesContext, users created here would be visible in other test classes
// (since the container is shared and not wiped between classes).
// With @DirtiesContext(classMode = AFTER_CLASS): context (and Flyway) reruns for
// the next class — the users table is re-migrated fresh.
//
// NOTE: @DirtiesContext is a performance cost (new context = slower startup).
// A better production strategy is @Sql("classpath:cleanup.sql") before each test.
// We use @DirtiesContext here to keep the code simple and focused on the concepts.
// ============================================================================
// @EnabledIfDockerAvailable: if Docker is unreachable (e.g., Windows + Docker Desktop 4.x
// where /info returns an empty response), the test class is SKIPPED, not failed.
// This is the correct behaviour for CI pipelines — Docker is a prerequisite, not a guarantee.
@EnabledIfDockerAvailable
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthIntegrationTest extends AbstractIntegrationTest {

    // MockMvc is autowired from the full context (configured by @AutoConfigureMockMvc).
    // Unlike in @WebMvcTest, this MockMvc runs through the REAL security filter chain.
    // That means JwtAuthenticationFilter actually executes on every request.
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // -----------------------------------------------------------------------
    // POST /api/v1/auth/register
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("register: creates user and returns JWT token")
    void register_validRequest_returnsToken() throws Exception {
        // Build the request just like a real API client would
        RegisterRequest request = new RegisterRequest();
        setField(request, "username", "integration_user");
        setField(request, "password", "password123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                // token must be present and non-null — we don't assert its value
                // because it changes every run (it contains a timestamp)
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.username", is("integration_user")));
    }

    @Test
    @DisplayName("register: returns 409 when username already exists")
    void register_duplicateUsername_returns409() throws Exception {
        // Register the user once
        RegisterRequest request = new RegisterRequest();
        setField(request, "username", "duplicate_user");
        setField(request, "password", "password123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Try to register again with the same username
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())  // 409 — UsernameAlreadyExistsException
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("register: returns 400 when username is too short")
    void register_shortUsername_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        setField(request, "username", "ab");  // min 3 chars — @Size(min=3) violation
        setField(request, "password", "password123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.errors.username").exists());
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/auth/login
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("login: returns JWT token for valid credentials")
    void login_validCredentials_returnsToken() throws Exception {
        // First register a user so there is someone to log in as
        RegisterRequest reg = new RegisterRequest();
        setField(reg, "username", "login_user");
        setField(reg, "password", "mypassword");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk());

        // Now login with the same credentials
        LoginRequest login = new LoginRequest();
        setField(login, "username", "login_user");
        setField(login, "password", "mypassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.username", is("login_user")));
    }

    @Test
    @DisplayName("login: returns 403 for wrong password")
    void login_wrongPassword_returns403() throws Exception {
        // Register a user
        RegisterRequest reg = new RegisterRequest();
        setField(reg, "username", "wrong_pass_user");
        setField(reg, "password", "correctpassword");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk());

        // Try to login with the wrong password
        LoginRequest login = new LoginRequest();
        setField(login, "username", "wrong_pass_user");
        setField(login, "password", "wrongpassword");

        // Spring Security's DaoAuthenticationProvider throws BadCredentialsException
        // which results in a 403 Forbidden (our SecurityConfig uses stateless, no login form)
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isForbidden());
    }

    // -----------------------------------------------------------------------
    // Helper — set private fields on request objects that have no setters
    // (RegisterRequest and LoginRequest use @Getter + @NoArgsConstructor only)
    // In real test code you'd add an @AllArgsConstructor or use Jackson directly.
    // We use reflection here to keep the production DTOs clean.
    // -----------------------------------------------------------------------
    private static void setField(Object target, String fieldName, String value)
            throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
