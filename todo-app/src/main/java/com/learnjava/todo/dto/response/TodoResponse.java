package com.learnjava.todo.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Schema(description = "A todo item returned by the API")
@Getter
@Builder
public class TodoResponse {

    @Schema(description = "The server-assigned unique identifier", example = "1")
    private final Long id;

    @Schema(description = "The title of the todo", example = "Buy groceries")
    private final String title;

    @Schema(description = "Optional details about the todo", example = "Milk, eggs, bread")
    private final String description;

    @Schema(description = "Whether the todo has been completed", example = "false")
    private final Boolean completed;

    // @JsonFormat — controls how LocalDateTime is serialized to JSON.
    // Without this, Jackson uses the default array format: [2024,1,15,10,30,0]
    // With this, it produces a readable ISO string: "2024-01-15T10:30:00"
    // This is what every API client (frontend, mobile) expects.
    @Schema(description = "When the todo was created", example = "2024-01-15T10:30:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime createdAt;

    @Schema(description = "When the todo was last updated", example = "2024-01-15T11:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime updatedAt;
}
