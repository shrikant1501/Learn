package com.learnjava.todo.controller;

import com.learnjava.todo.dto.request.CreateTodoRequest;
import com.learnjava.todo.dto.request.UpdateTodoRequest;
import com.learnjava.todo.dto.response.TodoResponse;
import com.learnjava.todo.exception.TodoNotFoundException;
import com.learnjava.todo.service.TodoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

// @Tag groups this controller under a named section in Swagger UI
// Without @Tag, SpringDoc uses the class name — less readable
@Tag(name = "Todos", description = "CRUD operations for managing todo items")
@Slf4j
@RestController
@RequestMapping("/api/v1/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    // =========================================================================
    // GET /api/v1/todos
    // =========================================================================

    // @Operation — the short summary shown next to the endpoint in Swagger UI
    @Operation(summary = "Get all todos", description = "Returns a list of all todo items. Empty list if none exist.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved list")
    @GetMapping
    public ResponseEntity<List<TodoResponse>> getAllTodos() {
        log.info("GET /api/v1/todos");
        return ResponseEntity.ok(todoService.getAllTodos());
    }

    // =========================================================================
    // GET /api/v1/todos/{id}
    // =========================================================================

    @Operation(summary = "Get a todo by ID", description = "Returns a single todo item by its unique identifier.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Todo found"),
        @ApiResponse(responseCode = "404", description = "Todo not found — no item with this ID exists"),
        @ApiResponse(responseCode = "400", description = "Invalid ID format — must be a number")
    })
    @GetMapping("/{id}")
    // @Parameter documents the path variable in the Swagger UI
    public ResponseEntity<TodoResponse> getTodoById(
            @Parameter(description = "The ID of the todo to retrieve", example = "1")
            @PathVariable Long id) {
        log.info("GET /api/v1/todos/{}", id);
        TodoResponse response = todoService.getTodoById(id)
                .orElseThrow(() -> new TodoNotFoundException(id));
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // POST /api/v1/todos
    // =========================================================================

    @Operation(summary = "Create a new todo", description = "Creates a new todo item. The server assigns the ID.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Todo created — Location header contains the new resource URL"),
        @ApiResponse(responseCode = "400", description = "Validation failed — title is required")
    })
    @PostMapping
    public ResponseEntity<TodoResponse> createTodo(@Valid @RequestBody CreateTodoRequest request) {
        log.info("POST /api/v1/todos - title: {}", request.getTitle());
        TodoResponse created = todoService.createTodo(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    // =========================================================================
    // PUT /api/v1/todos/{id}
    // =========================================================================

    @Operation(summary = "Update a todo", description = "Fully replaces an existing todo. All fields are overwritten.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Todo updated successfully"),
        @ApiResponse(responseCode = "404", description = "Todo not found"),
        @ApiResponse(responseCode = "400", description = "Validation failed")
    })
    @PutMapping("/{id}")
    public ResponseEntity<TodoResponse> updateTodo(
            @Parameter(description = "The ID of the todo to update", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody UpdateTodoRequest request) {
        log.info("PUT /api/v1/todos/{}", id);
        TodoResponse updated = todoService.updateTodo(id, request)
                .orElseThrow(() -> new TodoNotFoundException(id));
        return ResponseEntity.ok(updated);
    }

    // =========================================================================
    // DELETE /api/v1/todos/{id}
    // =========================================================================

    @Operation(summary = "Delete a todo", description = "Permanently deletes a todo item by ID.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Todo deleted — no content returned"),
        @ApiResponse(responseCode = "404", description = "Todo not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTodo(
            @Parameter(description = "The ID of the todo to delete", example = "1")
            @PathVariable Long id) {
        log.info("DELETE /api/v1/todos/{}", id);
        if (!todoService.deleteTodo(id)) {
            throw new TodoNotFoundException(id);
        }
        return ResponseEntity.noContent().build();
    }
}
