package com.learnjava.todo.service.impl;

import com.learnjava.todo.config.CacheConfig;
import com.learnjava.todo.dto.request.CreateTodoRequest;
import com.learnjava.todo.dto.request.TodoFilterRequest;
import com.learnjava.todo.dto.request.UpdateTodoRequest;
import com.learnjava.todo.dto.response.PagedResponse;
import com.learnjava.todo.dto.response.TodoResponse;
import com.learnjava.todo.model.Todo;
import com.learnjava.todo.model.User;
import com.learnjava.todo.repository.TodoRepository;
import com.learnjava.todo.security.SecurityUtil;
import com.learnjava.todo.service.TodoMapper;
import com.learnjava.todo.service.TodoService;
import com.learnjava.todo.service.TodoSpecification;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@Transactional
public class TodoServiceImpl implements TodoService {

    private final TodoRepository todoRepository;
    private final TodoMapper todoMapper;
    private final SecurityUtil securityUtil;
    private final Counter todosCreatedCounter;

    // Explicit constructor: SecurityUtil is injected; Counter is built from MeterRegistry.
    public TodoServiceImpl(TodoRepository todoRepository,
                           TodoMapper todoMapper,
                           SecurityUtil securityUtil,
                           MeterRegistry meterRegistry) {
        this.todoRepository = todoRepository;
        this.todoMapper = todoMapper;
        this.securityUtil = securityUtil;
        this.todosCreatedCounter = Counter.builder("todos.created")
                .description("Total number of todos created")
                .register(meterRegistry);
    }

    // Returns only the current user's todos (USER role) or all todos (ADMIN role).
    // The ownership filter is baked into the Specification — one DB call, no post-filtering.
    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TodoResponse> getTodos(TodoFilterRequest filter, Pageable pageable) {
        log.debug("Fetching todos — filter: {}, page: {}, size: {}",
                filter, pageable.getPageNumber(), pageable.getPageSize());

        // ADMIN sees all; USER sees only their own
        User owner = securityUtil.isAdmin() ? null : securityUtil.getCurrentUser();
        Specification<Todo> spec = TodoSpecification.fromFilter(filter, owner);
        Page<Todo> page = todoRepository.findAll(spec, pageable);

        return PagedResponse.<TodoResponse>builder()
                .content(page.getContent().stream().map(todoMapper::toResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    // Returns the todo only if the caller owns it (USER) or regardless (ADMIN).
    // Returns empty — not a 403 — when a user requests someone else's todo.
    // This "security through obscurity" prevents resource enumeration attacks.
    @Override
    @Cacheable(value = CacheConfig.TODOS_CACHE, key = "#id")
    @Transactional(readOnly = true)
    public Optional<TodoResponse> getTodoById(Long id) {
        log.debug("Cache MISS — loading todo {} from database", id);
        if (securityUtil.isAdmin()) {
            // ADMIN: plain lookup — can retrieve any todo by id
            return todoRepository.findById(id).map(todoMapper::toResponse);
        }
        // USER: ownership-scoped lookup — returns empty if todo belongs to someone else
        User currentUser = securityUtil.getCurrentUser();
        return todoRepository.findByIdAndOwner(id, currentUser)
                .map(todoMapper::toResponse);
    }

    // Assigns the current user as the owner before saving.
    @Override
    public TodoResponse createTodo(CreateTodoRequest request) {
        log.debug("Creating todo with title: {}", request.getTitle());
        Todo todo = todoMapper.toModel(request);
        // Set the owner to the currently authenticated user.
        // The mapper ignores "owner" (per @Mapping(target="owner", ignore=true)),
        // so we set it explicitly here after mapping.
        todo.setOwner(securityUtil.getCurrentUser());
        Todo saved = todoRepository.save(todo);
        log.debug("Created todo {} owned by '{}'", saved.getId(), saved.getOwner().getUsername());
        todosCreatedCounter.increment();
        return todoMapper.toResponse(saved);
    }

    // Updates only if the caller owns the todo (USER) or regardless (ADMIN).
    @Override
    @CacheEvict(value = CacheConfig.TODOS_CACHE, key = "#id")
    public Optional<TodoResponse> updateTodo(Long id, UpdateTodoRequest request) {
        log.debug("Updating todo {} — evicting from cache", id);
        Optional<Todo> found = securityUtil.isAdmin()
                ? todoRepository.findById(id)
                : todoRepository.findByIdAndOwner(id, securityUtil.getCurrentUser());

        return found.map(existingTodo -> {
            todoMapper.updateModel(existingTodo, request);
            Todo saved = todoRepository.save(existingTodo);
            return todoMapper.toResponse(saved);
        });
    }

    // Deletes only if the caller owns the todo (USER) or regardless (ADMIN).
    @Override
    @CacheEvict(value = CacheConfig.TODOS_CACHE, key = "#id")
    public boolean deleteTodo(Long id) {
        log.debug("Deleting todo {} — evicting from cache", id);
        if (securityUtil.isAdmin()) {
            if (!todoRepository.existsById(id)) return false;
            todoRepository.deleteById(id);
            return true;
        }
        // USER: only delete if they own it
        User currentUser = securityUtil.getCurrentUser();
        return todoRepository.findByIdAndOwner(id, currentUser)
                .map(todo -> {
                    todoRepository.delete(todo);
                    return true;
                })
                .orElse(false);
    }
}
