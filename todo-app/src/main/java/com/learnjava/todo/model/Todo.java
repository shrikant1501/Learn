package com.learnjava.todo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "todos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Todo extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Boolean completed;

    // @ManyToOne — many todos can belong to one user.
    // The FK column in the "todos" table is "user_id" (set by V6 migration).
    //
    // FetchType.LAZY: do NOT join the users table on every todo query.
    // The User object is loaded on demand only when getOwner() is called.
    // This is the correct default for @ManyToOne — prevents N+1 query surprises
    // and unnecessary data loading when we only need the todo's own fields.
    //
    // nullable = true (default): seed data todos (V3) have no owner.
    // All API-created todos will always have an owner set by the service layer.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User owner;
}
