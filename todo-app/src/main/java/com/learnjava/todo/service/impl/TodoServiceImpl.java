package com.learnjava.todo.service.impl;

import com.learnjava.todo.dto.request.CreateTodoRequest;
import com.learnjava.todo.dto.request.UpdateTodoRequest;
import com.learnjava.todo.dto.response.TodoResponse;
import com.learnjava.todo.model.Todo;
import com.learnjava.todo.repository.TodoRepository;
import com.learnjava.todo.service.TodoMapper;
import com.learnjava.todo.service.TodoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
// @Transactional at class level — every public method runs inside a DB transaction.
// If a method throws a RuntimeException, Hibernate rolls back all DB changes made in that call.
// read-only methods should override with @Transactional(readOnly = true) for a performance hint.
@Transactional
public class TodoServiceImpl implements TodoService {

    // Constructor injection — same pattern as the controller.
    // TodoRepository is injected by Spring; we never instantiate it ourselves.
    private final TodoRepository todoRepository;

    public TodoServiceImpl(TodoRepository todoRepository) {
        this.todoRepository = todoRepository;
    }

    // readOnly = true tells Hibernate: "this transaction will not modify data".
    // Hibernate skips dirty-checking (scanning for changed fields) — a performance gain.
    // Always use readOnly on GET-style service methods.
    @Override
    @Transactional(readOnly = true)
    public List<TodoResponse> getAllTodos() {
        log.debug("Fetching all todos");
        // findAll() → SELECT * FROM todos — returns List<Todo>
        // TodoMapper.toResponseList() converts each Todo to TodoResponse via streams
        return TodoMapper.toResponseList(todoRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TodoResponse> getTodoById(Long id) {
        log.debug("Looking up todo with id: {}", id);
        // findById() → SELECT * FROM todos WHERE id = ?
        // Returns Optional<Todo> — empty if no row found.
        // .map(TodoMapper::toResponse) converts Todo → TodoResponse if present.
        return todoRepository.findById(id)
                .map(TodoMapper::toResponse);
    }

    @Override
    public TodoResponse createTodo(CreateTodoRequest request) {
        log.debug("Creating todo with title: {}", request.getTitle());
        // Convert request DTO → domain model (no id yet — DB assigns it)
        Todo todo = TodoMapper.toModel(request);
        // save() on a new entity (id == null) → INSERT INTO todos (...)
        // Hibernate populates the id field on the returned object after the insert.
        Todo saved = todoRepository.save(todo);
        log.debug("Created todo with id: {}", saved.getId());
        return TodoMapper.toResponse(saved);
    }

    @Override
    public Optional<TodoResponse> updateTodo(Long id, UpdateTodoRequest request) {
        log.debug("Attempting to update todo with id: {}", id);
        // existsById() → SELECT COUNT(*) > 0 FROM todos WHERE id = ?
        // Cheaper than findById() when we only need to check existence.
        if (!todoRepository.existsById(id)) {
            return Optional.empty();
        }
        // toModel(id, request) builds a Todo with the URL-path id stamped on it.
        // save() on an entity WITH an id → UPDATE todos SET ... WHERE id = ?
        Todo updated = todoRepository.save(TodoMapper.toModel(id, request));
        return Optional.of(TodoMapper.toResponse(updated));
    }

    @Override
    public boolean deleteTodo(Long id) {
        log.debug("Attempting to delete todo with id: {}", id);
        // existsById first, then delete — gives us the boolean result the controller needs.
        if (!todoRepository.existsById(id)) {
            return false;
        }
        // deleteById() → DELETE FROM todos WHERE id = ?
        todoRepository.deleteById(id);
        return true;
    }
}
