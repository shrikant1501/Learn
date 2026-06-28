package com.learnjava.todo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnjava.todo.dto.request.CreateTodoRequest;
import com.learnjava.todo.dto.request.UpdateTodoRequest;
import com.learnjava.todo.dto.response.TodoResponse;
import com.learnjava.todo.exception.TodoNotFoundException;
import com.learnjava.todo.service.TodoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @WebMvcTest loads ONLY the web layer — controllers, filters, @ControllerAdvice.
// It does NOT load: services, repositories, JPA, the database.
// This makes it fast and focused: we test the HTTP contract, not the business logic.
//
// @WebMvcTest(TodoController.class) — scope it to our one controller for even faster startup.
@WebMvcTest(TodoController.class)
class TodoControllerTest {

    // MockMvc — lets us send HTTP requests without starting a real server.
    // Spring auto-configures it for us inside @WebMvcTest.
    @Autowired
    private MockMvc mockMvc;

    // ObjectMapper — Jackson's JSON serializer/deserializer.
    // We use it to convert request objects into JSON strings for the request body.
    @Autowired
    private ObjectMapper objectMapper;

    // @MockBean — creates a Mockito mock AND registers it as a Spring bean.
    // Because @WebMvcTest doesn't load the service layer, TodoController would fail
    // to start without a TodoService bean. @MockBean provides that bean as a mock.
    // This is different from @Mock (Mockito only) — @MockBean integrates with Spring.
    @MockBean
    private TodoService todoService;

    private TodoResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleResponse = TodoResponse.builder()
                .id(1L)
                .title("Learn Spring Boot")
                .description("Phase 7 testing")
                .completed(false)
                .build();
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/todos
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/todos: returns 200 with list of todos")
    void getAllTodos_returns200WithList() throws Exception {
        when(todoService.getAllTodos()).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/v1/todos"))
                .andExpect(status().isOk())
                // jsonPath("$") — root of the JSON response
                // jsonPath("$", hasSize(1)) — the root array has 1 element
                .andExpect(jsonPath("$", hasSize(1)))
                // jsonPath("$[0].id") — first element's id field
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].title", is("Learn Spring Boot")));
    }

    @Test
    @DisplayName("GET /api/v1/todos: returns 200 with empty list when no todos")
    void getAllTodos_returns200WithEmptyList() throws Exception {
        when(todoService.getAllTodos()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/todos/{id}
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/todos/{id}: returns 200 with todo when found")
    void getTodoById_found_returns200() throws Exception {
        when(todoService.getTodoById(1L)).thenReturn(Optional.of(sampleResponse));

        mockMvc.perform(get("/api/v1/todos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Learn Spring Boot")))
                .andExpect(jsonPath("$.completed", is(false)));
    }

    @Test
    @DisplayName("GET /api/v1/todos/{id}: returns 404 when not found")
    void getTodoById_notFound_returns404() throws Exception {
        // The service returns empty → controller throws TodoNotFoundException
        // → GlobalExceptionHandler returns 404
        when(todoService.getTodoById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/todos/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("NOT_FOUND")));
    }

    @Test
    @DisplayName("GET /api/v1/todos/{id}: returns 400 when id is not a number")
    void getTodoById_invalidId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/todos/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/todos
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/todos: returns 201 with created todo and Location header")
    void createTodo_validRequest_returns201() throws Exception {
        CreateTodoRequest request = CreateTodoRequest.builder()
                .title("New task")
                .description("Details here")
                .completed(false)
                .build();

        TodoResponse created = TodoResponse.builder()
                .id(4L).title("New task").description("Details here").completed(false)
                .build();

        when(todoService.createTodo(any(CreateTodoRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/v1/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        // objectMapper.writeValueAsString() converts the request object to JSON
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())                      // 201
                .andExpect(jsonPath("$.id", is(4)))
                .andExpect(jsonPath("$.title", is("New task")))
                // The Location header should point to the new resource
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/v1/todos/4")));

        // Confirm the service was called with a CreateTodoRequest
        verify(todoService).createTodo(any(CreateTodoRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/todos: returns 400 when title is blank")
    void createTodo_blankTitle_returns400WithValidationError() throws Exception {
        CreateTodoRequest request = CreateTodoRequest.builder()
                .title("")   // blank — violates @NotBlank
                .build();

        mockMvc.perform(post("/api/v1/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_FAILED")))
                // The errors map must contain a message for the title field
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    @DisplayName("POST /api/v1/todos: returns 400 when body is missing")
    void createTodo_missingBody_returns400() throws Exception {
        // No .content() → no request body → Jackson throws HttpMessageNotReadableException
        mockMvc.perform(post("/api/v1/todos")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // PUT /api/v1/todos/{id}
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("PUT /api/v1/todos/{id}: returns 200 with updated todo when found")
    void updateTodo_found_returns200() throws Exception {
        UpdateTodoRequest request = UpdateTodoRequest.builder()
                .title("Updated title")
                .completed(true)
                .build();

        TodoResponse updated = TodoResponse.builder()
                .id(1L).title("Updated title").completed(true)
                .build();

        when(todoService.updateTodo(eq(1L), any(UpdateTodoRequest.class)))
                .thenReturn(Optional.of(updated));

        mockMvc.perform(put("/api/v1/todos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Updated title")))
                .andExpect(jsonPath("$.completed", is(true)));
    }

    @Test
    @DisplayName("PUT /api/v1/todos/{id}: returns 404 when todo does not exist")
    void updateTodo_notFound_returns404() throws Exception {
        UpdateTodoRequest request = UpdateTodoRequest.builder()
                .title("Anything")
                .build();

        when(todoService.updateTodo(eq(99L), any(UpdateTodoRequest.class)))
                .thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/todos/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/v1/todos/{id}: returns 400 when title is blank")
    void updateTodo_blankTitle_returns400() throws Exception {
        UpdateTodoRequest request = UpdateTodoRequest.builder()
                .title("  ")   // whitespace — violates @NotBlank
                .build();

        mockMvc.perform(put("/api/v1/todos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_FAILED")));
    }

    // -----------------------------------------------------------------------
    // DELETE /api/v1/todos/{id}
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("DELETE /api/v1/todos/{id}: returns 204 when deleted")
    void deleteTodo_found_returns204() throws Exception {
        when(todoService.deleteTodo(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/v1/todos/1"))
                .andExpect(status().isNoContent());  // 204 — no body

        verify(todoService).deleteTodo(1L);
    }

    @Test
    @DisplayName("DELETE /api/v1/todos/{id}: returns 404 when todo does not exist")
    void deleteTodo_notFound_returns404() throws Exception {
        when(todoService.deleteTodo(99L)).thenReturn(false);

        mockMvc.perform(delete("/api/v1/todos/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }
}
