package com.learnjava.todo.service;

import com.learnjava.todo.dto.request.CreateTodoRequest;
import com.learnjava.todo.dto.request.UpdateTodoRequest;
import com.learnjava.todo.dto.response.TodoResponse;
import com.learnjava.todo.model.Todo;

import java.util.List;

/**
 * Converts between the {@link Todo} domain model and its DTO representations.
 *
 * <p>
 * <strong>Why is this in the {@code service} package?</strong><br>
 * The mapper sits at the service layer boundary. It is used:
 * <ul>
 *   <li>In the service: to convert request DTOs into domain objects (input)</li>
 *   <li>In the service: to convert domain objects into response DTOs (output)</li>
 * </ul>
 * Controllers never touch the {@code Todo} model — they only see DTOs.
 * Services never touch DTOs directly in business logic — they work with the model.
 * The mapper is the bridge between these two worlds.
 *
 * <p>
 * <strong>Why a class with static methods and not a Spring {@code @Component}?</strong><br>
 * Mapping is pure data transformation — no external dependencies, no I/O,
 * no state. There is no benefit to making Spring manage it. A class with
 * static utility methods is simpler and more honest about what it is.
 * It also makes tests easier: no Spring context needed to call a static method.
 *
 * <p>
 * <strong>Why not put this logic directly in the service?</strong><br>
 * Single Responsibility Principle. The service has one reason to change:
 * business logic changes. The mapper has one reason to change: the DTO shape
 * or model structure changes. Mixing them gives the service two reasons to
 * change — a violation of SRP.
 *
 * <p>
 * <strong>In a future phase</strong>, we will replace this hand-written mapper
 * with MapStruct — an annotation processor that generates this code automatically.
 * By writing it by hand first, you fully understand what MapStruct is saving you from.
 *
 * <p>
 * The constructor is private because this class should never be instantiated —
 * all methods are static utilities.
 */
public final class TodoMapper {

    // Prevent instantiation — this is a pure utility class
    private TodoMapper() {}

    // =========================================================================
    // Request DTOs → Domain Model
    // =========================================================================

    /**
     * Converts a {@link CreateTodoRequest} into a new {@link Todo} domain object.
     *
     * <p>
     * Note: the {@code id} is NOT set here. The ID is assigned by the service
     * (currently {@code AtomicLong}, later the database). This method produces
     * an "unsaved" Todo — a model object without an identity yet.
     *
     * @param request the incoming create request
     * @return a new Todo with no ID assigned
     */
    public static Todo toModel(CreateTodoRequest request) {
        return Todo.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .completed(request.getCompleted() != null ? request.getCompleted() : false)
                .build();
    }

    /**
     * Converts an {@link UpdateTodoRequest} into a {@link Todo} domain object.
     *
     * <p>
     * The {@code id} parameter is explicitly passed in because the ID lives in the
     * URL path (authoritative), not in the request body. The mapper receives it as
     * a parameter and sets it on the model — making the intent crystal clear.
     *
     * @param id      the authoritative ID from the URL path
     * @param request the incoming update request
     * @return a Todo with all fields set, ready to replace the stored version
     */
    public static Todo toModel(Long id, UpdateTodoRequest request) {
        return Todo.builder()
                .id(id)
                .title(request.getTitle())
                .description(request.getDescription())
                .completed(request.getCompleted())
                .build();
    }

    // =========================================================================
    // Domain Model → Response DTO
    // =========================================================================

    /**
     * Converts a {@link Todo} domain object into a {@link TodoResponse} DTO.
     *
     * <p>
     * This is the outbound conversion — called before sending data to clients.
     * Only the fields we explicitly include here will appear in the JSON response.
     * Internal fields (future: version, createdAt, etc.) are simply not mapped.
     *
     * @param todo the domain object to convert
     * @return a response DTO safe to expose to clients
     */
    public static TodoResponse toResponse(Todo todo) {
        return TodoResponse.builder()
                .id(todo.getId())
                .title(todo.getTitle())
                .description(todo.getDescription())
                .completed(todo.getCompleted())
                .build();
    }

    /**
     * Converts a list of {@link Todo} domain objects into a list of {@link TodoResponse} DTOs.
     *
     * <p>
     * Uses the Java Stream API for the conversion:
     * {@code .stream()} — creates a stream from the list
     * {@code .map(TodoMapper::toResponse)} — applies toResponse() to each element
     * {@code .toList()} — collects results into an immutable list (Java 16+)
     *
     * <p>
     * {@code TodoMapper::toResponse} is a method reference — shorthand for
     * {@code todo -> TodoMapper.toResponse(todo)}.
     *
     * @param todos the list of domain objects
     * @return a list of response DTOs
     */
    public static List<TodoResponse> toResponseList(List<Todo> todos) {
        return todos.stream()
                .map(TodoMapper::toResponse)
                .toList();
    }
}
