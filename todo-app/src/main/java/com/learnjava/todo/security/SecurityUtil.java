package com.learnjava.todo.security;

import com.learnjava.todo.model.User;

// Interface for accessing the current authenticated principal.
// Using an interface allows Mockito to mock via JDK proxy (no ByteBuddy needed on Java 25).
// The implementation reads from Spring Security's SecurityContextHolder.
public interface SecurityUtil {

    // Returns the currently authenticated User entity from the SecurityContext.
    User getCurrentUser();

    // Returns true if the current user has the ADMIN role.
    boolean isAdmin();
}
