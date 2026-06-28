package com.learnjava.todo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTodoRequest {

    // @NotBlank = not null AND not empty AND not just whitespace
    // "   " would pass @NotNull and @NotEmpty but fail @NotBlank — the right choice for titles
    // message = the text returned in the validation error response to the client
    @NotBlank(message = "Title must not be blank")
    @Size(min = 1, max = 255, message = "Title must be between 1 and 255 characters")
    private String title;

    // description is optional — no @NotBlank
    // but if provided, we cap it at 1000 chars to prevent abuse
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    // completed is optional — the service defaults it to false if omitted
    private Boolean completed;
}
