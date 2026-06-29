package com.learnjava.todo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

// UserDetails is Spring Security's contract for a "user principal".
// By implementing it directly on the entity, Spring Security can load our
// User object from the DB and use it as the authenticated principal immediately.
// No separate adapter/wrapper class is needed.
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
// extends Auditable — users table also gets created_at and updated_at.
// Useful for: "when did this user register?", "when did they last update their profile?"
public class User extends Auditable implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // username must be unique — it is the login identifier
    @Column(nullable = false, unique = true)
    private String username;

    // password is stored as a BCrypt hash, never plain text
    // columnDefinition = "TEXT" because BCrypt hashes are ~60 chars
    @Column(nullable = false, columnDefinition = "TEXT")
    private String password;

    // -----------------------------------------------------------------------
    // UserDetails contract — Spring Security reads these methods
    // -----------------------------------------------------------------------

    // Roles/authorities this user has. We keep it simple — no roles yet (Phase 18 adds RBAC).
    // Returning an empty list means the user is authenticated but has no specific roles.
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    // These four methods let Spring Security know the account is fully active.
    // In a production app you'd add DB columns (accountLocked, credentialsExpired, etc.)
    // and return real values. For now, always return true.
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
