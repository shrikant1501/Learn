package com.learnjava.todo.repository;

import com.learnjava.todo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// Spring Data JPA generates the SELECT * FROM users WHERE username = ? query
// automatically from the method name findByUsername.
// This is called a "derived query" — no @Query annotation needed.
public interface UserRepository extends JpaRepository<User, Long> {

    // Used by Spring Security's UserDetailsService to load a user by their login name.
    Optional<User> findByUsername(String username);
}
