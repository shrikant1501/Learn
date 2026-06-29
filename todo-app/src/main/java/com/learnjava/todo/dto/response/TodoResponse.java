package com.learnjava.todo.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Schema(description = "A todo item returned by the API")
@Getter
@Builder
// NON_NULL prevents "ownedBy": null from appearing in the JSON response for seed todos
// that were created without an authenticated user (V3 migration rows).
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TodoResponse {

    @Schema(description = "The server-assigned unique identifier", example = "1")
    private final Long id;

    @Schema(description = "The title of the todo", example = "Buy groceries")
    private final String title;

    @Schema(description = "Optional details about the todo", example = "Milk, eggs, bread")
    private final String description;

    @Schema(description = "Whether the todo has been completed", example = "false")
    private final Boolean completed;

    // The username of the user who created this todo.
    // Null for seed data todos (created without authentication context).
    // Hidden from JSON when null via @JsonInclude(NON_NULL) — avoids "ownedBy": null noise.
    @Schema(description = "Username of the owner", example = "alice")
    private final String ownedBy;

    @Schema(description = "When the todo was created", example = "2024-01-15T10:30:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime createdAt;

    @Schema(description = "When the todo was last updated", example = "2024-01-15T11:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime updatedAt;
}
