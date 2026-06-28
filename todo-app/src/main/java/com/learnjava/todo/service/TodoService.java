package com.learnjava.todo.service;

import com.learnjava.todo.dto.request.CreateTodoRequest;
import com.learnjava.todo.dto.request.UpdateTodoRequest;
import com.learnjava.todo.dto.response.TodoResponse;

import java.util.List;
import java.util.Optional;

/**
 * Contract for all Todo business operations.
 *
 * <p>
 * <strong>Phase 4 change — DTOs on the interface boundary:</strong><br>
 * The service now accepts request DTOs and returns response DTOs.
 * The {@code Todo} domain model is an <em>implementation detail</em> of the
 * service layer — callers (controllers) should never see it.
 *
 * <p>
 * This is the clean architecture boundary in action:
 * <ul>
 *   <li>Controller → Service: passes {@code CreateTodoRequest} / {@code UpdateTodoRequest}</li>
 *   <li>Service → Controller: returns {@code TodoResponse} / {@code List<TodoResponse>}</li>
 *   <li>{@code Todo} model: lives entirely inside the service layer, hidden from above</li>
 * </ul>
 *
 * <p>
 * When we add JPA in Phase 5, the {@code Todo} class becomes a {@code @Entity}.
 * Because it's hidden behind this interface, the controller and all callers
 * are completely unaware of that change. Zero ripple effect.
 */
public interface TodoService {

    /**
     * Retrieves all todo items.
     *
     * @return a list of response DTOs; never null, may be empty
     */
    List<TodoResponse> getAllTodos();

    /**
     * Retrieves a single todo by its unique identifier.
     *
     * @param id the ID to look up
     * @return an Optional containing the response DTO if found, or empty if not found
     */
    Optional<TodoResponse> getTodoById(Long id);

    /**
     * Creates a new todo item from the given request.
     *
     * @param request the validated create request (no ID — server assigns it)
     * @return the created todo as a response DTO, with its assigned ID
     */
    TodoResponse createTodo(CreateTodoRequest request);

    /**
     * Replaces an existing todo item entirely.
     *
     * @param id      the ID of the todo to update (from the URL path)
     * @param request the new data for the todo
     * @return an Optional containing the updated response DTO, or empty if not found
     */
    Optional<TodoResponse> updateTodo(Long id, UpdateTodoRequest request);

    /**
     * Deletes a todo item by its ID.
     *
     * @param id the ID of the todo to delete
     * @return {@code true} if the todo existed and was deleted, {@code false} if not found
     */
    boolean deleteTodo(Long id);
}
