package com.learnjava.todo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Request body for updating an existing todo item")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTodoRequest {

    @Schema(description = "The new title for the todo", example = "Buy groceries — updated")
    @NotBlank(message = "Title must not be blank")
    @Size(min = 1, max = 255, message = "Title must be between 1 and 255 characters")
    private String title;

    @Schema(description = "The new description", example = "Also need butter")
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @Schema(description = "The new completion status", example = "true")
    private Boolean completed;
}
