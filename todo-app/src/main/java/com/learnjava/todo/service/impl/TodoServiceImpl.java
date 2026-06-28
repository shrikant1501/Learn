package com.learnjava.todo.service.impl;

import com.learnjava.todo.dto.request.CreateTodoRequest;
import com.learnjava.todo.dto.request.UpdateTodoRequest;
import com.learnjava.todo.dto.response.TodoResponse;
import com.learnjava.todo.model.Todo;
import com.learnjava.todo.service.TodoMapper;
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
 * <strong>Phase 4 change:</strong> The service now uses {@link TodoMapper} to
 * convert between the {@link Todo} domain model and DTOs. The internal store
 * still holds {@code Todo} objects — that is the private implementation detail.
 * What changes externally (across the service boundary) are the DTOs.
 *
 * <p>
 * Notice the clear separation of concerns inside each method:
 * <ol>
 *   <li>Convert inbound DTO → domain model (via mapper)</li>
 *   <li>Execute business logic on domain model</li>
 *   <li>Convert result domain model → response DTO (via mapper)</li>
 *   <li>Return the response DTO</li>
 * </ol>
 * The controller above this layer only ever sees DTOs.
 * The store below this layer only ever holds {@code Todo} domain objects.
 * This class is the translator between the two worlds.
 */
@Slf4j
@Service
public class TodoServiceImpl implements TodoService {

    private final Map<Long, Todo> store = new HashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    public TodoServiceImpl() {
        Todo todo1 = Todo.builder()
                .id(idSequence.getAndIncrement())
                .title("Learn Spring Boot")
                .description("Complete Phase 4 of the todo project")
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
     * Fetches all todos from the store and converts each to a response DTO.
     * Stream + map + toList() is the idiomatic Java way to transform a collection.
     */
    @Override
    public List<TodoResponse> getAllTodos() {
        log.debug("Fetching all todos. Current store size: {}", store.size());
        return TodoMapper.toResponseList(new ArrayList<>(store.values()));
    }

    /**
     * Looks up a todo by ID and wraps the mapped response in an Optional.
     * If the ID is not found, store.get() returns null → Optional.empty().
     * If found, the Todo is mapped to TodoResponse → Optional.of(response).
     */
    @Override
    public Optional<TodoResponse> getTodoById(Long id) {
        log.debug("Looking up todo with id: {}", id);
        return Optional.ofNullable(store.get(id))
                .map(TodoMapper::toResponse);
    }

    /**
     * Converts the request DTO to a domain model, assigns a fresh ID,
     * stores it, and returns the mapped response DTO.
     *
     * <p>
     * Notice the flow:
     * CreateTodoRequest → TodoMapper.toModel() → Todo (no ID yet)
     * → assign ID → store → TodoMapper.toResponse() → TodoResponse
     */
    @Override
    public TodoResponse createTodo(CreateTodoRequest request) {
        long newId = idSequence.getAndIncrement();

        /*
         * toModel() produces a Todo without an ID.
         * We then build the final stored Todo by setting the generated ID.
         * Why not set the ID inside toModel()? Because the mapper should not
         * know about ID generation — that is the service's responsibility.
         */
        Todo todo = TodoMapper.toModel(request);
        Todo savedTodo = Todo.builder()
                .id(newId)
                .title(todo.getTitle())
                .description(todo.getDescription())
                .completed(todo.getCompleted())
                .build();

        store.put(newId, savedTodo);
        log.debug("Created todo with id: {}", newId);
        return TodoMapper.toResponse(savedTodo);
    }

    /**
     * Converts the update request + URL id to a domain model, replaces the
     * stored entry, and returns the mapped response DTO.
     */
    @Override
    public Optional<TodoResponse> updateTodo(Long id, UpdateTodoRequest request) {
        log.debug("Attempting to update todo with id: {}", id);

        if (!store.containsKey(id)) {
            log.debug("Todo with id {} not found for update", id);
            return Optional.empty();
        }

        /*
         * toModel(id, request) passes the URL-path ID explicitly to the mapper.
         * The mapper stamps it onto the built Todo. The request body's id (if any)
         * is structurally absent from UpdateTodoRequest — it can't sneak in.
         */
        Todo updatedTodo = TodoMapper.toModel(id, request);
        store.put(id, updatedTodo);
        log.debug("Updated todo with id: {}", id);
        return Optional.of(TodoMapper.toResponse(updatedTodo));
    }

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
