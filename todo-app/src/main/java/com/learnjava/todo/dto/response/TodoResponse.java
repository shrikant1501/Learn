package com.learnjava.todo.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * Response DTO representing a Todo item as seen by API consumers.
 *
 * <p>
 * <strong>Why {@code @Getter} and not {@code @Data}?</strong><br>
 * A response DTO flows in one direction only: from server to client.
 * Once built inside the service layer, it should never be mutated.
 * {@code @Data} would generate setters, which opens the door to accidental
 * mutation. {@code @Getter} + {@code @Builder} is the correct combination:
 * <ul>
 *   <li>{@code @Builder} — controlled, readable construction</li>
 *   <li>{@code @Getter} — read access for Jackson serialization</li>
 *   <li>No setters — immutability enforced</li>
 * </ul>
 *
 * <p>
 * <strong>This class is the public API contract.</strong><br>
 * It can evolve independently from {@code Todo} (the domain model).
 * For example:
 * <ul>
 *   <li>The database entity could add a {@code version} field for optimistic locking
 *       — we simply don't include it here, so clients never see it.</li>
 *   <li>We could add a computed field like {@code "daysUntilDue"} that doesn't
 *       exist in the database at all — DTOs aren't limited to what's stored.</li>
 *   <li>We could rename {@code "completed"} to {@code "done"} in the API without
 *       touching the database column — just change this DTO and the mapper.</li>
 * </ul>
 *
 * <p>
 * <strong>Why include {@code id} here but not in the request DTOs?</strong><br>
 * Clients need to know the ID of what they just created or retrieved, so they
 * can reference it in future requests (GET, PUT, DELETE /{id}).
 * On the request side, the client never dictates the ID — the server does.
 */
@Getter
@Builder
public class TodoResponse {

    /**
     * The server-assigned unique identifier.
     * Present in all responses — clients use this for subsequent operations.
     */
    private final Long id;

    /**
     * The todo title.
     */
    private final String title;

    /**
     * The optional description.
     */
    private final String description;

    /**
     * Whether the todo has been completed.
     */
    private final Boolean completed;
}
