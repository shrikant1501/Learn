package com.learnjava.todo.controller;

import com.learnjava.todo.model.Todo;
import com.learnjava.todo.service.TodoService;
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

/**
 * REST Controller for Todo resources.
 *
 * <p>
 * This controller exposes the full CRUD surface for the Todo resource.
 * It is a pure HTTP adapter — it:
 * <ol>
 *   <li>Receives HTTP requests</li>
 *   <li>Extracts data from the request (path variables, body)</li>
 *   <li>Delegates ALL logic to {@link TodoService}</li>
 *   <li>Translates the service result into the correct HTTP response</li>
 * </ol>
 * There is zero business logic here. No if-statements deciding what a "valid" todo is.
 * The controller's only decisions are: "what HTTP status does this service result map to?"
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    // =========================================================================
    // GET /api/v1/todos
    // =========================================================================

    /**
     * Retrieves all todo items.
     *
     * @return 200 OK with the list of all todos (empty list if none exist)
     */
    @GetMapping
    public ResponseEntity<List<Todo>> getAllTodos() {
        log.info("GET /api/v1/todos");
        return ResponseEntity.ok(todoService.getAllTodos());
    }

    // =========================================================================
    // GET /api/v1/todos/{id}
    // =========================================================================

    /**
     * Retrieves a single todo by ID.
     *
     * <p>
     * <strong>{@code @PathVariable} explained:</strong><br>
     * Binds the {id} segment from the URL to the {@code id} method parameter.
     * Spring automatically converts the String from the URL to {@code Long}.
     * If the conversion fails (e.g. "/todos/abc"), Spring returns 400 Bad Request.
     *
     * <p>
     * <strong>Pattern: Optional → ResponseEntity</strong><br>
     * {@code map(ResponseEntity::ok)} — if the Optional has a value, wrap it in 200 OK.
     * {@code orElse(ResponseEntity.notFound().build())} — if empty, return 404.
     * This is clean, functional, and requires no if-else.
     *
     * @param id the ID of the todo to retrieve
     * @return 200 OK with the todo, or 404 Not Found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Todo> getTodoById(@PathVariable Long id) {
        log.info("GET /api/v1/todos/{}", id);
        return todoService.getTodoById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // =========================================================================
    // POST /api/v1/todos
    // =========================================================================

    /**
     * Creates a new todo item.
     *
     * <p>
     * <strong>{@code @RequestBody} explained:</strong><br>
     * Jackson deserializes the JSON request body into a {@code Todo} object.
     * For example: {@code {"title": "Buy milk", "completed": false}}
     * becomes a {@code Todo} instance with those fields set.
     *
     * <p>
     * <strong>Why 201 and a Location header?</strong><br>
     * HTTP spec (RFC 7231) says: a POST that creates a resource SHOULD return
     * 201 Created AND a Location header pointing to the new resource's URL.
     * This allows clients to immediately fetch or bookmark the new resource
     * without needing to parse the response body.
     *
     * <p>
     * {@code ServletUriComponentsBuilder} builds the Location URI relative to
     * the current request URL, so it works regardless of host/port/context path.
     * Example result: {@code Location: http://localhost:8080/api/v1/todos/4}
     *
     * @param todo the todo data from the request body (id field is ignored)
     * @return 201 Created with the created todo and a Location header
     */
    @PostMapping
    public ResponseEntity<Todo> createTodo(@RequestBody Todo todo) {
        log.info("POST /api/v1/todos - title: {}", todo.getTitle());
        Todo created = todoService.createTodo(todo);

        /*
         * Build the URI for the Location header:
         * Start from current request URI (/api/v1/todos)
         * Append /{id} with the new todo's ID
         * Result: /api/v1/todos/4
         */
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

    /**
     * Replaces an existing todo entirely.
     *
     * <p>
     * <strong>PUT vs PATCH:</strong><br>
     * PUT = full replacement. Every field in the body replaces the stored value.
     * PATCH = partial update. Only specified fields are changed.
     * We implement PUT here (full replacement) — simpler and most common in REST APIs.
     * PATCH would be implemented in a later phase if needed.
     *
     * @param id   the ID of the todo to update (from URL path)
     * @param todo the new data for the todo (from request body)
     * @return 200 OK with the updated todo, or 404 Not Found
     */
    @PutMapping("/{id}")
    public ResponseEntity<Todo> updateTodo(@PathVariable Long id, @RequestBody Todo todo) {
        log.info("PUT /api/v1/todos/{}", id);
        return todoService.updateTodo(id, todo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // =========================================================================
    // DELETE /api/v1/todos/{id}
    // =========================================================================

    /**
     * Deletes a todo by ID.
     *
     * <p>
     * <strong>Why 204 No Content?</strong><br>
     * After deletion, there is nothing to return. The resource is gone.
     * 204 is the honest status code: "I did the work, there's nothing to say."
     * The response has no body.
     *
     * <p>
     * <strong>Why {@code ResponseEntity<Void>}?</strong><br>
     * {@code Void} is a type that can only be null — it signals "no body."
     * Using {@code ResponseEntity<?>} (wildcard) would also work, but
     * {@code Void} is more precise about the intent.
     *
     * @param id the ID of the todo to delete
     * @return 204 No Content if deleted, or 404 Not Found if it didn't exist
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTodo(@PathVariable Long id) {
        log.info("DELETE /api/v1/todos/{}", id);
        if (todoService.deleteTodo(id)) {
            return ResponseEntity.noContent().build();  // 204
        }
        return ResponseEntity.notFound().build();       // 404
    }
}
