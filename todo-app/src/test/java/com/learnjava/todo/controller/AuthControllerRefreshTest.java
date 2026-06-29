package com.learnjava.todo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnjava.todo.dto.auth.AuthResponse;
import com.learnjava.todo.dto.auth.RefreshTokenRequest;
import com.learnjava.todo.exception.InvalidRefreshTokenException;
import com.learnjava.todo.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @WebMvcTest(AuthController.class) — loads ONLY the AuthController and security beans.
// This tests the HTTP contract (request validation, response codes, body shape)
// without loading the database, Redis, or any other infrastructure.
@WebMvcTest(AuthController.class)
@WithMockUser
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-unit-tests-only-32chars",
        "jwt.expiration=900000",
        "jwt.refresh-token-expiration=604800000"
})
class AuthControllerRefreshTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // @MockBean: AuthController depends on AuthService — provide a mock
    @MockBean
    private AuthService authService;

    // Required by JwtAuthenticationFilter in the security filter chain
    @MockBean
    private com.learnjava.todo.security.JwtService jwtService;

    @MockBean
    private com.learnjava.todo.repository.UserRepository userRepository;

    // -----------------------------------------------------------------------
    // POST /auth/refresh
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST /auth/refresh: returns 200 with new token pair on valid refresh token")
    void refresh_returns200WithNewTokenPair() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");

        AuthResponse response = AuthResponse.builder()
                .token("new-access-token")
                .refreshToken("new-refresh-token")
                .username("alice")
                .build();

        when(authService.refresh(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"))
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    @DisplayName("POST /auth/refresh: returns 401 when refresh token is invalid or expired")
    void refresh_returns401ForInvalidToken() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("expired-token");

        when(authService.refresh(any())).thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /auth/refresh: returns 400 when refreshToken is blank")
    void refresh_returns400ForBlankToken() throws Exception {
        // An empty refreshToken fails @NotBlank validation before reaching the service
        RefreshTokenRequest request = new RefreshTokenRequest("");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // POST /auth/logout
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST /auth/logout: returns 204 on successful logout")
    void logout_returns204() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("some-refresh-token");

        doNothing().when(authService).logout(any());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        // Verify the service was called
        verify(authService).logout(any());
    }

    @Test
    @DisplayName("POST /auth/logout: returns 204 even if token is already expired (idempotent)")
    void logout_isIdempotent_doesNotThrow() throws Exception {
        // logout() is void and never throws — even for expired tokens.
        // The client considers itself logged out regardless.
        RefreshTokenRequest request = new RefreshTokenRequest("already-expired-token");

        doNothing().when(authService).logout(any());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /auth/logout: returns 400 when refreshToken is blank")
    void logout_returns400ForBlankToken() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
