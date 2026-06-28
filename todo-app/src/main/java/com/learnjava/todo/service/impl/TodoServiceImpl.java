package com.learnjava.todo.service.impl;

import com.learnjava.todo.model.Todo;
import com.learnjava.todo.service.TodoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of {@link TodoService}.
 *
 * <p>
 * <strong>Phase 2 note:</strong> We now use a {@code HashMap} to simulate a database.
 * This lets us exercise the full CRUD surface without JPA complexity.
 * In a later phase, this entire class will be replaced by a JPA repository,
 * and the controller and service interface will not change at all —
 * demonstrating the value of the interface/implementation separation.
 *
 * <p>
 * <strong>Why {@code HashMap} and not a {@code List}?</strong><br>
 * For a data store, we need to look up items by ID efficiently.
 * A {@code List} requires scanning every element: O(n).
 * A {@code HashMap} looks up by key in constant time: O(1).
 * This matches how a real database primary-key lookup works.
 *
 * <p>
 * <strong>Why {@code AtomicLong} for ID generation?</strong><br>
 * {@code AtomicLong} provides thread-safe increment without synchronization.
 * {@code getAndIncrement()} atomically reads the current value and increments it.
 * If we used a plain {@code long}, two concurrent requests could get the same ID.
 * In production, the database handles ID generation — this is our safe stand-in.
 */
@Slf4j
@Service
public class TodoServiceImpl implements TodoService {

    /*
     * Our in-memory "database".
     * Key   = todo ID (Long)
     * Value = the Todo object
     *
     * Not thread-safe for concurrent structural modifications, but sufficient
     * for our learning purposes. Production would use a real DB.
     */
    private final Map<Long, Todo> store = new HashMap<>();

    /*
     * ID generator. Starts at 1 and increments with each new todo.
     * AtomicLong is from java.util.concurrent — it guarantees that
     * getAndIncrement() is an atomic (indivisible) operation.
     */
    private final AtomicLong idSequence = new AtomicLong(1);

    /*
     * Pre-populate the store with some sample data so the API is
     * immediately useful without needing to POST todos first.
     * This is the constructor — it runs once when Spring creates this bean.
     */
    public TodoServiceImpl() {
        Todo todo1 = Todo.builder()
                .id(idSequence.getAndIncrement())
                .title("Learn Spring Boot")
                .description("Complete Phase 2 of the todo project")
                .completed(false)
                .build();

        Todo todo2 = Todo.builder()
                .id(idSequence.getAndIncrement())
                .title("Understand Dependency Injection")
                .description("Learn how Spring manages beans and wires dependencies")
                .completed(false)
                .build();

        Todo todo3 = Todo.builder()
                .id(idSequence.getAndIncrement())
                .title("Read about REST principles")
                .description("Understand statelessness, uniform interface, and resource naming")
                .completed(true)
                .build();

        store.put(todo1.getId(), todo1);
        store.put(todo2.getId(), todo2);
        store.put(todo3.getId(), todo3);

        log.debug("In-memory store initialised with {} todos", store.size());
    }

    /**
     * Returns all todos as an unordered list.
     *
     * <p>
     * {@code new ArrayList<>(store.values())} creates a defensive copy.
     * We never hand out a direct reference to our internal collection —
     * callers should not be able to mutate our store by modifying the list.
     */
    @Override
    public List<Todo> getAllTodos() {
        log.debug("Fetching all todos. Current store size: {}", store.size());
        return new ArrayList<>(store.values());
    }

    /**
     * Looks up a todo by ID.
     *
     * <p>
     * {@code Optional.ofNullable()} wraps the result:
     * - If store.get(id) returns a Todo → Optional.of(todo) → non-empty Optional
     * - If store.get(id) returns null    → Optional.empty() → empty Optional
     *
     * The caller (controller) is then FORCED to handle both cases.
     * It cannot accidentally call .getTitle() on null and get an NPE.
     */
    @Override
    public Optional<Todo> getTodoById(Long id) {
        log.debug("Looking up todo with id: {}", id);
        return Optional.ofNullable(store.get(id));
    }

    /**
     * Creates a new todo and stores it.
     *
     * <p>
     * Key design decision: the service assigns the ID, not the caller.
     * This mirrors database auto-increment behaviour.
     * Whatever id the caller sends in the body is ignored — we always
     * generate a fresh one. This prevents ID collisions and client manipulation.
     */
    @Override
    public Todo createTodo(Todo todo) {
        long newId = idSequence.getAndIncrement();
        /*
         * We build a NEW Todo object rather than mutating the incoming one.
         * Why? The incoming object belongs to the caller (the controller layer).
         * Mutating it would be a side effect — unexpected and hard to debug.
         * Building a new object keeps things clean and predictable.
         */
        Todo newTodo = Todo.builder()
                .id(newId)
                .title(todo.getTitle())
                .description(todo.getDescription())
                .completed(todo.getCompleted() != null ? todo.getCompleted() : false)
                .build();

        store.put(newId, newTodo);
        log.debug("Created todo with id: {}", newId);
        return newTodo;
    }

    /**
     * Replaces an existing todo entirely (PUT semantics).
     *
     * <p>
     * Returns {@code Optional.empty()} if the ID doesn't exist.
     * The controller translates this to a 404 response.
     *
     * <p>
     * Notice we preserve the original ID — the ID in the URL is the
     * authoritative identifier. Whatever id is in the request body is ignored.
     * The URL path is the resource address; the body is the new content.
     */
    @Override
    public Optional<Todo> updateTodo(Long id, Todo todo) {
        log.debug("Attempting to update todo with id: {}", id);

        if (!store.containsKey(id)) {
            log.debug("Todo with id {} not found for update", id);
            return Optional.empty();
        }

        Todo updatedTodo = Todo.builder()
                .id(id)                          // always preserve the original ID
                .title(todo.getTitle())
                .description(todo.getDescription())
                .completed(todo.getCompleted())
                .build();

        store.put(id, updatedTodo);
        log.debug("Updated todo with id: {}", id);
        return Optional.of(updatedTodo);
    }

    /**
     * Deletes a todo by ID.
     *
     * <p>
     * {@code store.remove(id)} returns the removed value, or null if key didn't exist.
     * We use {@code != null} to determine whether the delete actually removed something.
     * This boolean is used by the controller to decide between 204 and 404.
     */
    @Override
    public boolean deleteTodo(Long id) {
        log.debug("Attempting to delete todo with id: {}", id);
        boolean existed = store.remove(id) != null;
        if (existed) {
            log.debug("Deleted todo with id: {}", id);
        } else {
            log.debug("Todo with id {} not found for deletion", id);
        }
        return existed;
    }
}
