package com.learnjava.todo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the core Todo domain object.
 *
 * <p>
 * At this phase, this is a plain Java object (POJO).
 * In a later phase, we will annotate it with JPA annotations
 * ({@code @Entity}, {@code @Table}, etc.) to map it to a database table.
 * We keep it simple now intentionally — no premature complexity.
 *
 * <p>
 * <strong>Lombok annotations explained:</strong>
 * <ul>
 *   <li>{@code @Data} — generates getters, setters, equals(), hashCode(), toString()</li>
 *   <li>{@code @Builder} — implements the Builder design pattern for clean object construction:
 *       {@code Todo.builder().title("Buy milk").completed(false).build()}</li>
 *   <li>{@code @NoArgsConstructor} — generates a no-argument constructor (required by frameworks)</li>
 *   <li>{@code @AllArgsConstructor} — generates a constructor with all fields (required by @Builder)</li>
 * </ul>
 *
 * <p>
 * <strong>Why @Builder?</strong> When an object has many fields, using a constructor becomes
 * error-prone: {@code new Todo(1L, "title", false)} — what does {@code false} mean here?
 * Builder pattern makes it self-documenting:
 * {@code Todo.builder().id(1L).title("title").completed(false).build()}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Todo {

    /**
     * Unique identifier for the todo item.
     * Using Long (object type) instead of long (primitive) because:
     *   1. It can represent null (useful before the object is persisted to DB)
     *   2. JPA/frameworks expect object wrapper types for IDs
     */
    private Long id;

    /**
     * The main text describing what needs to be done.
     * Required field — a todo without a title is meaningless.
     */
    private String title;

    /**
     * Optional additional details about the todo item.
     */
    private String description;

    /**
     * Whether the task has been completed.
     * Defaults to false — a new todo is always incomplete.
     * Using Boolean (object) instead of boolean (primitive) for the same
     * null-representability reason as the id field.
     */
    private Boolean completed;
}
