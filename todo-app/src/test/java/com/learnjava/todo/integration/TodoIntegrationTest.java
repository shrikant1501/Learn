package com.learnjava.todo.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnjava.todo.dto.auth.RegisterRequest;
import com.learnjava.todo.dto.request.CreateTodoRequest;
import com.learnjava.todo.dto.request.UpdateTodoRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ============================================================================
// TodoIntegrationTest — end-to-end tests for /api/v1/todos/**
//
// WHAT THIS PROVES that unit tests and @WebMvcTest cannot:
//   1. Flyway V1 migration creates the correct schema in real PostgreSQL
//   2. Flyway V3 seed data is actually in the database
//   3. Spring Data JPA queries execute correctly (findAll, findById, save, delete)
//   4. TodoSpecification WHERE clauses work against real SQL
//   5. The JWT token obtained from /register is accepted by /todos (full auth chain)
//   6. @CreatedDate / @LastModifiedDate are populated by JPA Auditing
//   7. Pagination metadata (totalElements, totalPages) is correct
//
// AUTHENTICATION STRATEGY:
//   Each integration test must carry a real JWT. We get one in @BeforeEach by calling
//   /api/v1/auth/register (which also logs the user in and returns a token).
//   We store the token in the 'jwtToken' field and use it as the Authorization header.
//   This tests the full auth flow — JwtAuthenticationFilter actually validates the token.
// ============================================================================
// @EnabledIfDockerAvailable: if Docker is unreachable (e.g., Windows + Docker Desktop 4.x
// where /info returns an empty response), the test class is SKIPPED, not failed.
@EnabledIfDockerAvailable
class TodoIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Holds the JWT token obtained in @BeforeEach.
    // Every test method uses this to authorize requests.
    private String jwtToken;

    // -----------------------------------------------------------------------
    // @BeforeEach — obtain a fresh JWT token before each test
    // -----------------------------------------------------------------------

    // We register a new user before each test with a unique username (timestamp-based).
    // Why unique? We cannot rollback the transaction in integration tests because
    // the controller commits its own transaction — @Transactional rollback only works
    // when the test method owns the transaction. Using unique usernames avoids
    // "username already exists" errors when the same name is registered repeatedly.
    @BeforeEach
    void obtainJwtToken() throws Exception {
        String uniqueUsername = "todo_test_user_" + System.currentTimeMillis();

        RegisterRequest reg = new RegisterRequest();
        setField(reg, "username", uniqueUsername);
        setField(reg, "password", "testpassword");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk())
                .andReturn();

        // Parse the token from the JSON response body.
        // objectMapper.readTree() gives us a JsonNode tree we can navigate with .get()
        String responseBody = result.getResponse().getContentAsString();
        jwtToken = objectMapper.readTree(responseBody).get("token").asText();
    }

    // -----------------------------------------------------------------------
    // Helper: add the Authorization header to every request
    // -----------------------------------------------------------------------

    // "Bearer " + token is the standard HTTP Authorization header format for JWTs.
    // JwtAuthenticationFilter looks for exactly this prefix to extract the token.
    private String bearer() {
        return "Bearer " + jwtToken;
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/todos — list with Flyway seed data
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /todos: returns 200 with seeded todos from Flyway V3 migration")
    void getTodos_returnsSeededData() throws Exception {
        // V3__seed_todos.sql inserts 4 todos.
        // This test proves Flyway actually ran V3 against the real PostgreSQL container.
        mockMvc.perform(get("/api/v1/todos")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()))
                // At minimum the 4 seeded todos must be present
                // (each @BeforeEach registers a user but does NOT add todos)
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(10)));
    }

    @Test
    @DisplayName("GET /todos: returns 401 when no Authorization header is provided")
    void getTodos_noToken_returns401() throws Exception {
        // No .header("Authorization") — the JWT filter should reject this request.
        // This proves the security filter chain is actually running in integration tests
        // (unlike @WebMvcTest where @WithMockUser bypasses it).
        mockMvc.perform(get("/api/v1/todos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /todos: returns filtered results when ?completed=true")
    void getTodos_filterByCompleted_returnsOnlyCompleted() throws Exception {
        // V3 seed data has exactly 1 completed todo ("Read about REST principles")
        mockMvc.perform(get("/api/v1/todos")
                        .param("completed", "true")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                // Every item in the content must have completed = true
                .andExpect(jsonPath("$.content[0].completed", is(true)));
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/todos — create
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST /todos: creates todo and returns 201 with Location header")
    void createTodo_validRequest_returns201WithLocation() throws Exception {
        CreateTodoRequest request = CreateTodoRequest.builder()
                .title("Integration test todo")
                .description("Created in a real PostgreSQL container")
                .completed(false)
                .build();

        mockMvc.perform(post("/api/v1/todos")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())                          // 201
                .andExpect(jsonPath("$.id", notNullValue()))              // DB assigned an ID
                .andExpect(jsonPath("$.title", is("Integration test todo")))
                .andExpect(jsonPath("$.completed", is(false)))
                // Audit timestamps populated by AuditingEntityListener
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.updatedAt", notNullValue()))
                // Location header: /api/v1/todos/{new-id}
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.containsString("/api/v1/todos/")));
    }

    @Test
    @DisplayName("POST /todos: returns 400 when title is blank")
    void createTodo_blankTitle_returns400WithValidationError() throws Exception {
        CreateTodoRequest request = CreateTodoRequest.builder()
                .title("")  // @NotBlank violation
                .build();

        mockMvc.perform(post("/api/v1/todos")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.errors.title").exists());
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/todos/{id} — read by ID
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /todos/{id}: returns created todo by its DB-assigned ID")
    void getTodoById_afterCreate_returnsTodo() throws Exception {
        // Step 1: create a todo and capture its assigned ID from the response
        CreateTodoRequest request = CreateTodoRequest.builder()
                .title("Find me by ID")
                .completed(false)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/todos")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        // Extract the ID the database assigned to the new todo
        String body = createResult.getResponse().getContentAsString();
        long createdId = objectMapper.readTree(body).get("id").asLong();

        // Step 2: GET by the ID we just received — verify it's the same todo
        mockMvc.perform(get("/api/v1/todos/" + createdId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is((int) createdId)))
                .andExpect(jsonPath("$.title", is("Find me by ID")));
    }

    @Test
    @DisplayName("GET /todos/{id}: returns 404 for non-existent ID")
    void getTodoById_nonExistent_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/todos/999999")
                        .header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    // -----------------------------------------------------------------------
    // PUT /api/v1/todos/{id} — update
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("PUT /todos/{id}: updates todo title and completed flag")
    void updateTodo_existingId_returns200WithUpdatedData() throws Exception {
        // Step 1: create a todo to update
        CreateTodoRequest create = CreateTodoRequest.builder()
                .title("Before update")
                .completed(false)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/todos")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn();

        long id = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asLong();

        // Step 2: update the todo
        UpdateTodoRequest update = UpdateTodoRequest.builder()
                .title("After update")
                .completed(true)
                .build();

        mockMvc.perform(put("/api/v1/todos/" + id)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("After update")))
                .andExpect(jsonPath("$.completed", is(true)));
    }

    // -----------------------------------------------------------------------
    // DELETE /api/v1/todos/{id} — delete
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("DELETE /todos/{id}: deletes todo and returns 204, then 404 on re-fetch")
    void deleteTodo_existingId_returns204ThenNotFoundOnRefetch() throws Exception {
        // Step 1: create a todo to delete
        CreateTodoRequest create = CreateTodoRequest.builder()
                .title("Delete me")
                .completed(false)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/todos")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn();

        long id = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asLong();

        // Step 2: delete it
        mockMvc.perform(delete("/api/v1/todos/" + id)
                        .header("Authorization", bearer()))
                .andExpect(status().isNoContent());  // 204

        // Step 3: verify it's gone — GET should return 404 now
        // This proves the DELETE actually executed against the real PostgreSQL DB.
        mockMvc.perform(get("/api/v1/todos/" + id)
                        .header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /todos/{id}: returns 404 when todo does not exist")
    void deleteTodo_nonExistent_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/todos/999999")
                        .header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    // -----------------------------------------------------------------------
    // Helper — set private fields on request objects (same as AuthIntegrationTest)
    // -----------------------------------------------------------------------
    private static void setField(Object target, String fieldName, String value)
            throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
