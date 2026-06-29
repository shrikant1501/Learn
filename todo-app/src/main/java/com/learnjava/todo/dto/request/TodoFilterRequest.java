package com.learnjava.todo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

// Encapsulates all optional filter/search parameters for the todo list endpoint.
// Using a dedicated object instead of individual @RequestParam fields keeps
// the controller method signature clean and groups related params together.
// As filters grow (priority, dueDate, tags) this class absorbs them cleanly.
@Data
@Schema(description = "Optional filters for querying todos")
public class TodoFilterRequest {

    // null = no filter applied (return all regardless of status)
    // true = return only completed todos
    // false = return only incomplete todos
    @Schema(description = "Filter by completion status. Omit to return all.", example = "false")
    private Boolean completed;

    // null or blank = no search applied
    // non-blank = case-insensitive LIKE search on title AND description
    @Schema(description = "Keyword to search in title and description (case-insensitive)", example = "spring")
    private String search;
}
