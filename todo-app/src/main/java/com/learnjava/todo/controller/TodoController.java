package com.learnjava.todo.controller;

import com.learnjava.todo.model.Todo;
import com.learnjava.todo.service.TodoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller for Todo resources.
 *
 * <p>
 * Responsibilities of this class (and ONLY these responsibilities):
 * <ol>
 *   <li>Accept incoming HTTP requests</li>
 *   <li>Delegate to the service layer</li>
 *   <li>Return an appropriate HTTP response</li>
 * </ol>
 * The controller must contain <strong>zero business logic</strong>.
 * It is purely a translation layer between HTTP and Java.
 *
 * <p>
 * <strong>{@code @RestController}:</strong><br>
 * Combination of {@code @Controller} + {@code @ResponseBody}.
 * Every method's return value is automatically serialized to JSON
 * and written to the HTTP response body.
 *
 * <p>
 * <strong>{@code @RequestMapping("/api/v1/todos")}:</strong><br>
 * All endpoints in this controller are prefixed with this path.
 * By putting it at the class level, we avoid repeating it on every method.
 * This is the API versioning strategy — v1 in the URL.
 *
 * <p>
 * <strong>{@code @RequiredArgsConstructor} (Lombok):</strong><br>
 * Generates a constructor for all {@code final} fields. Since {@code todoService}
 * is declared {@code final}, Lombok creates:
 * <pre>
 *   public TodoController(TodoService todoService) {
 *       this.todoService = todoService;
 *   }
 * </pre>
 * Spring sees this constructor and performs <strong>constructor injection</strong>
 * automatically. This is the preferred injection style over {@code @Autowired}
 * field injection because:
 * <ul>
 *   <li>Dependencies are explicit and visible</li>
 *   <li>The class can be instantiated in tests without Spring at all</li>
 *   <li>The {@code final} keyword guarantees the dependency is never null</li>
 *   <li>It's the style recommended by the Spring team itself</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/todos")
@RequiredArgsConstructor
public class TodoController {

    /*
     * Note: we depend on the INTERFACE (TodoService), not the implementation
     * (TodoServiceImpl). The controller has no knowledge of how todos are
     * fetched — that's the service's concern.
     *
     * The `final` keyword here is crucial:
     *   - It forces the dependency to be set at construction time
     *   - Combined with @RequiredArgsConstructor, it enables constructor injection
     *   - It makes the dependency immutable after construction
     */
    private final TodoService todoService;

    /**
     * GET /api/v1/todos
     *
     * <p>
     * Retrieves all todo items.
     *
     * <p>
     * <strong>Why {@code ResponseEntity<List<Todo>>} and not just {@code List<Todo>}?</strong><br>
     * {@code ResponseEntity} gives us control over the entire HTTP response:
     * <ul>
     *   <li>Status code (200 OK, 201 Created, 404 Not Found, etc.)</li>
     *   <li>Response headers</li>
     *   <li>Response body</li>
     * </ul>
     * Returning just {@code List<Todo>} would work, but Spring would always
     * return 200. With {@code ResponseEntity}, we are explicit about the contract.
     * This becomes essential when we return 201 for creation, 404 for not-found, etc.
     *
     * <p>
     * {@code ResponseEntity.ok(body)} is a factory method that creates a
     * response with status 200 OK and the given body.
     *
     * @return HTTP 200 with a JSON array of all todos
     */
    @GetMapping
    public ResponseEntity<List<Todo>> getAllTodos() {
        log.info("GET /api/v1/todos - Retrieving all todos");
        List<Todo> todos = todoService.getAllTodos();
        log.info("Returning {} todo(s)", todos.size());
        return ResponseEntity.ok(todos);
    }
}
