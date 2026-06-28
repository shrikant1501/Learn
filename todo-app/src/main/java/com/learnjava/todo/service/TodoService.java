package com.learnjava.todo.service;

import com.learnjava.todo.model.Todo;

import java.util.List;
import java.util.Optional;

/**
 * Contract for all Todo business operations.
 *
 * <p>
 * This interface defines the full CRUD surface for the Todo resource.
 * The controller depends ONLY on this interface — it has zero awareness
 * of whether the data comes from memory, a database, or a remote API.
 *
 * <p>
 * Notice the use of {@code Optional<Todo>} for single-item retrieval and update.
 * This is the modern Java way to express "this operation might not find anything."
 * It forces callers to explicitly handle the "not found" case, eliminating
 * the risk of silent {@code NullPointerException}s.
 */
public interface TodoService {

    /**
     * Retrieves all todo items.
     *
     * @return a list of all todos; never null, may be empty
     */
    List<Todo> getAllTodos();

    /**
     * Retrieves a single todo by its unique identifier.
     *
     * @param id the ID to look up
     * @return an Optional containing the todo if found, or empty if not found
     */
    Optional<Todo> getTodoById(Long id);

    /**
     * Creates a new todo item.
     *
     * <p>
     * The caller should NOT provide an ID — the service assigns one.
     * This mirrors how real databases work: you insert data, the DB generates the key.
     *
     * @param todo the todo data to persist (id field is ignored/overwritten)
     * @return the created todo with its assigned ID
     */
    Todo createTodo(Todo todo);

    /**
     * Replaces an existing todo item entirely.
     *
     * <p>
     * This is a full replacement (PUT semantics), not a partial update (PATCH semantics).
     * Every field in the provided {@code todo} overwrites the stored value.
     *
     * @param id   the ID of the todo to update
     * @param todo the new data to store
     * @return an Optional containing the updated todo if found, or empty if not found
     */
    Optional<Todo> updateTodo(Long id, Todo todo);

    /**
     * Deletes a todo item by its ID.
     *
     * @param id the ID of the todo to delete
     * @return {@code true} if the todo existed and was deleted, {@code false} if not found
     */
    boolean deleteTodo(Long id);
}
