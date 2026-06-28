package com.learnjava.todo.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;

/**
 * Global exception handler for all REST controllers.
 *
 * <p>
 * <strong>{@code @RestControllerAdvice} explained:</strong><br>
 * This is a combination of {@code @ControllerAdvice} + {@code @ResponseBody}.
 * {@code @ControllerAdvice} marks this class as a cross-cutting concern that
 * applies to ALL controllers in the application. Spring registers it as a
 * special bean that intercepts exceptions <em>after</em> they leave a controller
 * method but <em>before</em> they reach the client.
 *
 * <p>
 * Think of it as a global try-catch that wraps every controller method invisibly.
 * Controllers throw exceptions freely; this class catches them and converts them
 * into clean HTTP responses. Controllers stay clean — they contain only happy path logic.
 *
 * <p>
 * <strong>How Spring picks the right handler method:</strong><br>
 * When an exception is thrown, Spring walks up the exception class hierarchy
 * looking for the most specific matching {@code @ExceptionHandler}. For example:
 * <ul>
 *   <li>{@code TodoNotFoundException} → matches {@code handleTodoNotFound} exactly</li>
 *   <li>{@code NullPointerException} → no specific handler → falls to {@code handleAllExceptions}</li>
 * </ul>
 * Spring always prefers the most specific match. This is exactly like Java's
 * own multi-catch block ordering rules.
 *
 * <p>
 * <strong>Why {@code HttpServletRequest} as a parameter?</strong><br>
 * Spring injects it automatically into any handler method that declares it.
 * We use it to extract the request path for the error response, so clients
 * know exactly which URL triggered the error.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // =========================================================================
    // 404 NOT FOUND — TodoNotFoundException
    // =========================================================================

    /**
     * Handles the case where a Todo with the requested ID does not exist.
     *
     * <p>
     * This is the most specific handler — it catches exactly our custom exception.
     * The controller throws {@code TodoNotFoundException} via {@code orElseThrow()},
     * and this method intercepts it cleanly.
     *
     * @param ex      the exception carrying the missing ID and message
     * @param request the incoming HTTP request (for extracting the path)
     * @return 404 Not Found with a structured error body
     */
    @ExceptionHandler(TodoNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTodoNotFound(
            TodoNotFoundException ex,
            HttpServletRequest request) {

        log.warn("Todo not found: {}", ex.getMessage());

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.NOT_FOUND.value())           // 404
                .error(HttpStatus.NOT_FOUND.name())             // "NOT_FOUND"
                .message(ex.getMessage())                        // "Todo with id 42 was not found"
                .path(request.getRequestURI())                  // "/api/v1/todos/42"
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // =========================================================================
    // 400 BAD REQUEST — Malformed JSON body
    // =========================================================================

    /**
     * Handles malformed or unreadable JSON in the request body.
     *
     * <p>
     * Thrown by Jackson when {@code @RequestBody} cannot deserialize the body.
     * Examples that trigger this:
     * <ul>
     *   <li>POST with body: {@code {invalid json}}</li>
     *   <li>POST with no Content-Type header</li>
     *   <li>POST with a number where a string is expected</li>
     * </ul>
     *
     * <p>
     * <strong>Important:</strong> We deliberately return a vague message
     * ("Malformed JSON request body") and do NOT echo back the raw exception
     * message. The raw message from Jackson can be very verbose and may reveal
     * internal class names — a security concern in production.
     *
     * @param ex      the Jackson deserialization exception
     * @param request the incoming HTTP request
     * @return 400 Bad Request with a structured error body
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        log.warn("Malformed JSON request at {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())         // 400
                .error(HttpStatus.BAD_REQUEST.name())           // "BAD_REQUEST"
                .message("Malformed JSON request body")
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // =========================================================================
    // 400 BAD REQUEST — Wrong path variable type
    // =========================================================================

    /**
     * Handles type mismatch for path variables and request parameters.
     *
     * <p>
     * Thrown when Spring cannot convert a URL segment to the required type.
     * Example: {@code GET /api/v1/todos/abc} — "abc" cannot be parsed to {@code Long}.
     *
     * <p>
     * Without this handler, Spring would return a raw 400 with an unformatted message.
     * We intercept it and return our consistent error structure.
     *
     * @param ex      the type mismatch exception
     * @param request the incoming HTTP request
     * @return 400 Bad Request with a clear message about what was wrong
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        /*
         * Build a clear, human-readable message.
         * ex.getName()       → the parameter name, e.g. "id"
         * ex.getValue()      → what the client sent, e.g. "abc"
         * ex.getRequiredType() → what we expected, e.g. "Long"
         */
        String message = String.format(
                "Parameter '%s' must be of type %s but received: '%s'",
                ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown",
                ex.getValue()
        );

        log.warn("Type mismatch at {}: {}", request.getRequestURI(), message);

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.name())
                .message(message)
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // =========================================================================
    // 500 INTERNAL SERVER ERROR — catch-all safety net
    // =========================================================================

    /**
     * Catch-all handler for any unexpected exception.
     *
     * <p>
     * This is the safety net. If an exception is thrown that has no specific
     * handler above, it falls here. This prevents Spring from ever returning
     * a raw stack trace or an unformatted error to the client.
     *
     * <p>
     * <strong>Critical production rule:</strong><br>
     * We log the FULL exception here (for server-side debugging), but we return
     * only a generic message to the client. Never leak internal details
     * (class names, stack traces, SQL errors) in HTTP responses — it is a
     * security vulnerability (information disclosure / CWE-209).
     *
     * @param ex      any unhandled exception
     * @param request the incoming HTTP request
     * @return 500 Internal Server Error with a generic message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllExceptions(
            Exception ex,
            HttpServletRequest request) {

        // Log the full exception with stack trace — only on the server side
        log.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())   // 500
                .error(HttpStatus.INTERNAL_SERVER_ERROR.name())     // "INTERNAL_SERVER_ERROR"
                .message("An unexpected error occurred. Please try again later.")
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
