package com.learnjava.todo.service;

import com.learnjava.todo.model.Todo;

import java.util.List;

/**
 * Contract for all Todo business operations.
 *
 * <p>
 * This is an interface, not a class. This is a deliberate architectural choice.
 *
 * <p>
 * <strong>Why an interface?</strong>
 * <ol>
 *   <li><strong>Dependency Inversion Principle (SOLID - D):</strong> High-level modules
 *       (Controller) should not depend on low-level modules (ServiceImpl). Both should
 *       depend on abstractions (this interface).</li>
 *   <li><strong>Testability:</strong> In unit tests, you can inject a mock of this
 *       interface into the controller without needing a real implementation. This is
 *       the foundation of clean unit testing.</li>
 *   <li><strong>Flexibility:</strong> You can have multiple implementations —
 *       e.g., {@code InMemoryTodoService} for now, and {@code DatabaseTodoService}
 *       later — and swap them with a single annotation change.</li>
 *   <li><strong>Clear contract:</strong> The interface documents exactly what operations
 *       are available, without exposing implementation details.</li>
 * </ol>
 *
 * <p>
 * The controller will only ever know about this interface.
 * It will never import or reference {@code TodoServiceImpl}.
 */
public interface TodoService {

    /**
     * Retrieves all todo items.
     *
     * @return a list of all todos; never null, may be empty
     */
    List<Todo> getAllTodos();
}
