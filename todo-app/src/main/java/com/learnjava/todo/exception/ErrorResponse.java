package com.learnjava.todo.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * A consistent, structured error response returned by the API for all failure scenarios.
 *
 * <p>
 * This is the <strong>error contract</strong> between our API and its consumers.
 * Every error — whether a 404, 400, or 500 — will be serialized into this exact shape.
 * API consumers (mobile apps, frontend clients) can parse this reliably.
 *
 * <p>
 * <strong>Why {@code @Getter} and not {@code @Data}?</strong><br>
 * {@code @Data} generates getters + setters + equals + hashCode + toString.
 * An error response is immutable — once created, it should never be modified.
 * We only need {@code @Getter} (for Jackson serialization) and {@code @Builder}
 * (for clean construction). {@code @Setter} would allow mutation, which we don't want.
 * Always use the most restrictive Lombok annotations that satisfy your requirements.
 *
 * <p>
 * <strong>Why {@code @Builder} and not a constructor?</strong><br>
 * We have 5 fields. A constructor would be:
 * {@code new ErrorResponse(404, "NOT_FOUND", "message", "/path", LocalDateTime.now())}
 * — hard to read, easy to get field order wrong.
 * Builder is self-documenting and immune to field-order mistakes.
 *
 * <p>
 * <strong>Sample JSON output:</strong>
 * <pre>
 * {
 *   "status": 404,
 *   "error": "NOT_FOUND",
 *   "message": "Todo with id 42 was not found",
 *   "path": "/api/v1/todos/42",
 *   "timestamp": "2026-06-29 12:00:00"
 * }
 * </pre>
 */
@Getter
@Builder
public class ErrorResponse {

    /**
     * The HTTP status code as an integer.
     * Duplicating it in the body (in addition to the HTTP response status)
     * makes it easier for clients to parse without reading HTTP headers.
     */
    private final int status;

    /**
     * A short, machine-readable error code.
     * Using the enum name (e.g., "NOT_FOUND", "BAD_REQUEST") rather than
     * the phrase ("Not Found") makes it easier for clients to switch on.
     */
    private final String error;

    /**
     * A human-readable explanation of what went wrong.
     * Should be specific enough to be useful for debugging:
     * "Todo with id 42 was not found" — not just "Not Found".
     */
    private final String message;

    /**
     * The request path that triggered the error.
     * Helps developers correlate this error with a specific API call.
     */
    private final String path;

    /**
     * When the error occurred, in server local time.
     *
     * <p>
     * {@code @JsonFormat} controls how Jackson serializes this field to JSON.
     * Without it, Jackson serializes {@code LocalDateTime} as an array:
     * {@code [2026, 6, 29, 12, 0, 0]} — unreadable.
     * With the pattern, it becomes: {@code "2026-06-29 12:00:00"} — readable.
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime timestamp;
}
