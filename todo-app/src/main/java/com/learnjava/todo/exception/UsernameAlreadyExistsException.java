package com.learnjava.todo.exception;

// Thrown when a registration attempt uses a username that already exists in the database.
// Maps to HTTP 409 Conflict in the GlobalExceptionHandler.
public class UsernameAlreadyExistsException extends RuntimeException {

    public UsernameAlreadyExistsException(String username) {
        super("Username already taken: " + username);
    }
}
