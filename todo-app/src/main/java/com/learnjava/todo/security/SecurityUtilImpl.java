package com.learnjava.todo.security;

import com.learnjava.todo.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

// Reads the currently authenticated user from Spring Security's SecurityContextHolder.
// Centralizing this here means the service layer never directly touches SecurityContextHolder —
// it depends on the SecurityUtil interface (mockable in tests).
@Component
public class SecurityUtilImpl implements SecurityUtil {

    @Override
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // getPrincipal() returns the UserDetails object set by JwtAuthenticationFilter.
        // In our app this is always a User entity (User implements UserDetails).
        return (User) auth.getPrincipal();
    }

    @Override
    public boolean isAdmin() {
        return SecurityContextHolder.getContext()
                .getAuthentication()
                .getAuthorities()
                .stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
