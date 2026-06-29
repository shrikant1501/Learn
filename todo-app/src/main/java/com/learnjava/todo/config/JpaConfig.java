package com.learnjava.todo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// @EnableJpaAuditing — activates Spring Data JPA's auditing infrastructure.
// This single annotation tells Spring to:
//   1. Register the AuditingEntityListener globally
//   2. Wire an AuditorAware bean (for @CreatedBy / @LastModifiedBy — Phase 16)
//   3. Intercept @PrePersist and @PreUpdate Hibernate events to populate
//      @CreatedDate and @LastModifiedDate fields automatically
//
// WHY a separate class?
//   We keep auditing config here instead of inside SecurityConfig or the main
//   application class because each @Configuration class should have one reason
//   to change (Single Responsibility Principle). JPA config changes independently
//   from security config.
@Configuration
@EnableJpaAuditing
public class JpaConfig {
    // No bean definitions needed yet.
    // In Phase 16 (RBAC), we'll add an AuditorAware<String> bean here
    // to automatically populate @CreatedBy and @LastModifiedBy with the
    // current authenticated user's username.
}
