package com.learnjava.todo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

// Generic wrapper for paginated API responses.
// T = the item type (e.g. TodoResponse)
// Using a generic class means this same wrapper can be reused for any resource
// in the future (UserResponse, OrderResponse, etc.) — one contract, many types.
@Getter
@Builder
@Schema(description = "A paginated response envelope containing items and pagination metadata")
public class PagedResponse<T> {

    @Schema(description = "The list of items for the current page")
    private final List<T> content;

    // Pagination metadata — clients need this to build "Next/Prev" buttons
    // and to know when they've reached the last page.

    @Schema(description = "Current page number (0-based)", example = "0")
    private final int page;

    @Schema(description = "Number of items per page", example = "10")
    private final int size;

    @Schema(description = "Total number of items across all pages", example = "42")
    private final long totalElements;

    @Schema(description = "Total number of pages", example = "5")
    private final int totalPages;

    @Schema(description = "Whether this is the last page", example = "false")
    private final boolean last;
}
