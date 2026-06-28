package com.learnjava.todo.service.impl;

import com.learnjava.todo.model.Todo;
import com.learnjava.todo.service.TodoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * In-memory implementation of {@link TodoService}.
 *
 * <p>
 * <strong>Phase 1 note:</strong> We are using hardcoded in-memory data.
 * This is intentional — it lets us build and test the full HTTP layer
 * without introducing database complexity. In a later phase, we will
 * replace this with a proper JPA repository and real persistence.
 *
 * <p>
 * <strong>{@code @Service} explained:</strong><br>
 * This is a Spring stereotype annotation. It tells Spring's component scanner:
 * "Create an instance of this class and manage it in the ApplicationContext
 * (the IoC container)." That managed instance is called a <em>bean</em>.
 * {@code @Service} is semantically equivalent to {@code @Component} but
 * communicates intent: this class contains business logic.
 *
 * <p>
 * <strong>{@code @Slf4j} explained (Lombok):</strong><br>
 * Generates a private static final {@code log} field using SLF4J.
 * Without Lombok, you'd write:
 * <pre>
 *   private static final Logger log = LoggerFactory.getLogger(TodoServiceImpl.class);
 * </pre>
 * SLF4J is a logging <em>facade</em> — it abstracts over the actual logging
 * framework (Logback, Log4j2, etc.). Spring Boot uses Logback by default.
 * Always log through SLF4J, never directly through Logback or Log4j2.
 */
@Slf4j
@Service
public class TodoServiceImpl implements TodoService {

    /**
     * Returns a hardcoded list of todos for Phase 1.
     *
     * <p>
     * Notice the use of {@code List.of()} instead of {@code new ArrayList<>()}.
     * {@code List.of()} returns an <strong>immutable</strong> list — you cannot
     * add or remove elements from it. This is intentional: our service returns
     * a snapshot of data, and callers should not be able to mutate the internal
     * state by modifying the returned list.
     *
     * <p>
     * We use the {@code @Builder} pattern on Todo here for readability.
     */
    @Override
    public List<Todo> getAllTodos() {
        log.debug("Fetching all todos from in-memory store");

        return List.of(
                Todo.builder()
                        .id(1L)
                        .title("Learn Spring Boot")
                        .description("Complete Phase 1 of the todo project")
                        .completed(false)
                        .build(),

                Todo.builder()
                        .id(2L)
                        .title("Understand Dependency Injection")
                        .description("Learn how Spring manages beans and wires dependencies")
                        .completed(false)
                        .build(),

                Todo.builder()
                        .id(3L)
                        .title("Read about REST principles")
                        .description("Understand statelessness, uniform interface, and resource naming")
                        .completed(true)
                        .build(),

                         Todo.builder()
                        .id(4L)
                        .title("Read about DB")
                        .description("Understand connection, constraints")
                        .completed(true)
                        .build()
        );
    }
}
