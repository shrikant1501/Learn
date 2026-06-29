package com.learnjava.todo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

// @EnableJpaAuditing — activates Spring Data JPA's auditing infrastructure.
// auditorAwareRef = "auditorProvider": tells Spring which bean to call when it needs
// to know the current user (for @CreatedBy / @LastModifiedBy population).
// The bean name must match the @Bean method name below exactly.
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaConfig {

    // AuditorAware<String> — Spring Data JPA calls this every time an entity is
    // saved (@PrePersist) or updated (@PreUpdate) to get the current user's identity.
    // The returned String is stored in the @CreatedBy or @LastModifiedBy column.
    //
    // WHY Optional.empty() as fallback?
    // On app startup, Flyway migrations run before any user is authenticated.
    // The SecurityContext is empty during migration. Returning Optional.empty() tells
    // JPA "no auditor available" — the column stays null for those rows (seeded data).
    // For all user-triggered operations, Authentication is present and we return the username.
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            // auth is null if no JWT has been processed (unauthenticated request)
            // auth.isAuthenticated() = false for AnonymousAuthenticationToken
            if (auth == null || !auth.isAuthenticated()
                    || "anonymousUser".equals(auth.getPrincipal())) {
                return Optional.empty();
            }
            return Optional.of(auth.getName()); // auth.getName() = the username from the JWT
        };
    }
}
