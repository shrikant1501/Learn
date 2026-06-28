package com.learnjava.todo.exception;

/**
 * Thrown when a Todo resource cannot be found by its ID.
 *
 * <p>
 * <strong>Why extend {@code RuntimeException} and not {@code Exception}?</strong><br>
 * Java has two kinds of exceptions:
 * <ul>
 *   <li><strong>Checked exceptions</strong> ({@code extends Exception}) — the compiler
 *       forces every caller to either catch them or declare them with {@code throws}.
 *       Useful for recoverable conditions where the caller can meaningfully react
 *       (e.g., {@code FileNotFoundException} — maybe try a different path).</li>
 *   <li><strong>Unchecked exceptions</strong> ({@code extends RuntimeException}) — no
 *       compile-time enforcement. They propagate up the call stack freely until caught.
 *       Useful for programming errors or conditions the caller cannot recover from.</li>
 * </ul>
 *
 * <p>
 * "Todo not found" is a condition the controller layer cannot recover from locally —
 * the right response is always a 404. Forcing every service method to declare
 * {@code throws TodoNotFoundException} would pollute every interface method signature
 * with noise. {@code RuntimeException} is the right choice here and is the
 * convention in Spring applications.
 *
 * <p>
 * <strong>Why store the {@code id}?</strong><br>
 * It allows us to generate a precise, informative error message:
 * {@code "Todo with id 42 was not found"} — much more useful than a generic
 * {@code "Resource not found"} for debugging and for API consumers.
 */
public class TodoNotFoundException extends RuntimeException {

    /*
     * The ID that was requested but not found.
     * Stored so the GlobalExceptionHandler can include it in the error response.
     */
    private final Long id;

    /**
     * Creates a new exception for a missing Todo with the given ID.
     *
     * <p>
     * We call {@code super(message)} to set the exception's message, which is
     * what {@code ex.getMessage()} returns in the exception handler.
     * The message is built eagerly here — the format is decided by the domain
     * layer, not the HTTP layer.
     *
     * @param id the ID that was not found
     */
    public TodoNotFoundException(Long id) {
        super("Todo with id " + id + " was not found");
        this.id = id;
    }

    /**
     * Returns the ID that triggered this exception.
     *
     * @return the missing todo's ID
     */
    public Long getId() {
        return id;
    }
}
