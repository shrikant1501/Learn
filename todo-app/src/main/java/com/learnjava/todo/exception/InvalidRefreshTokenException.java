package com.learnjava.todo.exception;

// Thrown when a refresh token is not found in Redis —
// meaning it expired, was already rotated, or is invalid.
// Maps to HTTP 401 Unauthorized in GlobalExceptionHandler.
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token is invalid or has expired");
    }
}
