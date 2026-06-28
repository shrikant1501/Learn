package com.learnjava.todo.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new Todo item.
 *
 * <p>
 * <strong>What is a DTO (Data Transfer Object)?</strong><br>
 * A DTO is a plain object whose sole purpose is to carry data between layers
 * or across a network boundary. It has no business logic — just fields,
 * getters, and setters. The pattern was formalized by Martin Fowler in
 * "Patterns of Enterprise Application Architecture."
 *
 * <p>
 * <strong>Why no {@code id} field?</strong><br>
 * The client must not supply an ID when creating a resource. The server
 * (and later the database) is the authoritative source of IDs. Omitting
 * the field from this DTO makes it structurally impossible for the client
 * to send one — the JSON field is simply ignored even if provided.
 * This is better than accepting it and silently ignoring it.
 *
 * <p>
 * <strong>Why {@code @Data} here but not on {@code ErrorResponse}?</strong><br>
 * {@code ErrorResponse} is immutable — once created it should not change.
 * A request DTO is populated by Jackson's deserialization, which needs
 * setters (or a no-args constructor + setters). {@code @Data} provides
 * both getters and setters, which is appropriate here.
 *
 * <p>
 * <strong>Future: validation annotations will go here</strong><br>
 * In a later phase, fields in this class will be annotated with Bean Validation:
 * <pre>
 *   {@code @NotBlank(message = "Title is required")}
 *   private String title;
 * </pre>
 * That's why this class exists separately from the model — validation rules
 * belong on the input boundary, not on the database entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTodoRequest {

    /**
     * The title of the todo. Will be required (non-blank) in a future validation phase.
     */
    private String title;

    /**
     * An optional longer description of the todo.
     */
    private String description;

    /**
     * Initial completion status. Defaults to false in the service if not provided.
     * Using {@code Boolean} (nullable wrapper) intentionally — the client may omit
     * this field entirely, in which case the service applies the default.
     */
    private Boolean completed;
}
