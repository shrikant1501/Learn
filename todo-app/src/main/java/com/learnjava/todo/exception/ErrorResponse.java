package com.learnjava.todo.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
// @JsonInclude(NON_NULL) — fields that are null are omitted from the JSON output entirely.
// The 'errors' map is only present for validation failures.
// For a 404 or 500, 'errors' will be null and therefore absent from the response — clean output.
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final int status;
    private final String error;
    private final String message;
    private final String path;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime timestamp;

    // Present only on validation errors (400 VALIDATION_FAILED).
    // Key = field name (e.g. "title"), Value = constraint message (e.g. "must not be blank").
    // Null for all other error types — omitted from JSON by @JsonInclude(NON_NULL).
    private final Map<String, String> errors;
}
