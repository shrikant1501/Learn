package com.learnjava.todo.repository;

import com.learnjava.todo.model.Todo;
import com.learnjava.todo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TodoRepository extends JpaRepository<Todo, Long>,
        JpaSpecificationExecutor<Todo> {
    // JpaRepository<Todo, Long>       → findAll, findById, save, deleteById etc.
    // JpaSpecificationExecutor<Todo>  → findAll(Specification, Pageable) for filtering

    // Ownership-scoped lookup: find a todo by id only if it belongs to the given owner.
    // Used by getTodoById, updateTodo, deleteTodo — silently returns empty for
    // todos the caller does not own (404 instead of 403 — no information disclosure).
    // Spring Data generates: SELECT * FROM todos WHERE id = ? AND user_id = ?
    Optional<Todo> findByIdAndOwner(Long id, User owner);
}
