package com.learnjava.todo.model;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// @MappedSuperclass — tells JPA: "include these fields in every subclass table,
// but do NOT create a separate table for Auditable itself."
// This is the correct approach for shared audit fields across multiple entities.
// The alternative (@Inheritance) would create a parent table — wrong for audit fields.
@MappedSuperclass

// @EntityListeners — registers Spring's AuditingEntityListener on every entity
// that extends this class. This listener hooks into Hibernate's lifecycle events:
//   @PrePersist  → fires before INSERT → populates createdAt and updatedAt
//   @PreUpdate   → fires before UPDATE → populates only updatedAt
// Without this annotation, @CreatedDate and @LastModifiedDate do nothing.
@EntityListeners(AuditingEntityListener.class)

// @Getter for reading the audit values in mappers and responses.
// @Setter is needed so AuditingEntityListener can write the values via reflection.
// We don't expose setters in the public API — they're package-accessible for the listener.
@Getter
@Setter
public abstract class Auditable {

    // @CreatedDate — Spring sets this ONCE when the entity is first persisted.
    // On all subsequent saves, the listener sees it is already non-null and skips it.
    // updatable = false — even if someone accidentally calls save() on an existing entity,
    // the DB column definition itself rejects any attempt to overwrite this value.
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // @LastModifiedDate — Spring sets this on EVERY insert AND update.
    // After a PUT /api/v1/todos/{id}, this field reflects the exact time of the change.
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
