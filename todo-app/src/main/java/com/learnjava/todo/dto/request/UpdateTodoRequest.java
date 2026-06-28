package com.learnjava.todo.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for replacing an existing Todo item (PUT semantics).
 *
 * <p>
 * <strong>Why is this separate from {@link CreateTodoRequest}?</strong><br>
 * Today the fields are identical. But their purposes — and future evolution — differ:
 * <ul>
 *   <li>Create: all required fields must be present; the server assigns the ID.</li>
 *   <li>Update: the ID comes from the URL path; the body replaces the resource entirely.</li>
 * </ul>
 * In a future validation phase, the constraints will differ:
 * <ul>
 *   <li>{@code CreateTodoRequest.title} → {@code @NotBlank} (mandatory on creation)</li>
 *   <li>{@code UpdateTodoRequest.title} → may allow null for partial updates later</li>
 * </ul>
 * Having two classes costs nothing now and gives us full flexibility later.
 * Having one class would force us to use hacks like "nullable means don't update"
 * alongside "nullable means field is optional on create" — two different meanings
 * for the same null value. That's a design smell.
 *
 * <p>
 * <strong>No {@code id} field — for the same reason as {@link CreateTodoRequest}.</strong><br>
 * The ID is authoritative in the URL path. Whatever appears in the body is irrelevant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTodoRequest {

    /**
     * The new title for the todo. Replaces the existing value entirely.
     */
    private String title;

    /**
     * The new description. A null value will replace the existing description with null.
     */
    private String description;

    /**
     * The new completion status. Replaces the existing value.
     */
    private Boolean completed;
}
