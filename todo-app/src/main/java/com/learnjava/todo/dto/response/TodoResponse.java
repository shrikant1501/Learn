package com.learnjava.todo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

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
}
