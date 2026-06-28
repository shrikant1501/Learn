package com.learnjava.todo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// @Schema(description) on the class = the description shown for this model in Swagger UI
@Schema(description = "Request body for creating a new todo item")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTodoRequest {

    // @Schema on fields = description + example value shown in the UI
    // The example is what Swagger pre-fills when a user clicks "Try it out"
    @Schema(description = "The title of the todo", example = "Buy groceries")
    @NotBlank(message = "Title must not be blank")
    @Size(min = 1, max = 255, message = "Title must be between 1 and 255 characters")
    private String title;

    @Schema(description = "Optional details about the todo", example = "Milk, eggs, bread")
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @Schema(description = "Whether the todo is already completed", example = "false")
    private Boolean completed;
}
