package com.learnjava.todo.controller;

import com.learnjava.todo.dto.request.CreateTodoRequest;
import com.learnjava.todo.dto.request.UpdateTodoRequest;
import com.learnjava.todo.dto.response.TodoResponse;
import com.learnjava.todo.exception.TodoNotFoundException;
import com.learnjava.todo.service.TodoService;
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

/**
 * REST Controller for Todo resources.
 *
 * <p>
 * <strong>Phase 4 change:</strong> The controller now works exclusively with DTOs.
 * Notice what is NOT imported here: {@code com.learnjava.todo.model.Todo}.
 * The controller has zero knowledge of the domain model — it only knows about
 * the shapes of data coming in ({@code CreateTodoRequest}, {@code UpdateTodoRequest})
 * and going out ({@code TodoResponse}).
 *
 * <p>
 * This is the clean architecture boundary enforced by the type system.
 * The compiler itself prevents the controller from accidentally accessing
 * any domain model fields or JPA annotations that will appear in Phase 5.
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

    @GetMapping
    public ResponseEntity<List<TodoResponse>> getAllTodos() {
        log.info("GET /api/v1/todos");
        return ResponseEntity.ok(todoService.getAllTodos());
    }

    // =========================================================================
    // GET /api/v1/todos/{id}
    // =========================================================================

    @GetMapping("/{id}")
    public ResponseEntity<TodoResponse> getTodoById(@PathVariable Long id) {
        log.info("GET /api/v1/todos/{}", id);
        TodoResponse response = todoService.getTodoById(id)
                .orElseThrow(() -> new TodoNotFoundException(id));
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // POST /api/v1/todos
    // =========================================================================

    /**
     * Creates a new todo.
     *
     * <p>
     * The method parameter is now {@code CreateTodoRequest} — not {@code Todo}.
     * This means the JSON body the client sends is structurally limited to
     * just {title, description, completed}. There is no {@code id} field.
     * Even if a client sends {@code "id": 999}, Jackson ignores it because
     * {@code CreateTodoRequest} has no {@code id} field to map it to.
     * The security improvement is structural — enforced by the type system.
     */
    @PostMapping
    // @Valid tells Spring: run Bean Validation on this parameter before calling this method.
    // If any @NotBlank or @Size constraint fails, Spring throws MethodArgumentNotValidException
    // immediately — the method body never executes. GlobalExceptionHandler catches it.
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

    @PutMapping("/{id}")
    public ResponseEntity<TodoResponse> updateTodo(
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTodo(@PathVariable Long id) {
        log.info("DELETE /api/v1/todos/{}", id);
        if (!todoService.deleteTodo(id)) {
            throw new TodoNotFoundException(id);
        }
        return ResponseEntity.noContent().build();
    }
}
