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
import lombok.NoArgsConstructor;

// @Entity — tells Hibernate "map this class to a database table"
// @Table  — explicitly names the table "todos" (otherwise Hibernate uses the class name)
@Entity
@Table(name = "todos")
@Data
@Builder
@NoArgsConstructor   // required by JPA — Hibernate needs a no-arg constructor to instantiate entities
@AllArgsConstructor  // required by @Builder
public class Todo {

    // @Id — marks this field as the primary key
    // @GeneratedValue(IDENTITY) — tells the DB to auto-increment the value
    //   IDENTITY strategy: DB generates the key on insert (works with H2 and PostgreSQL)
    //   We no longer need AtomicLong — the database handles ID generation
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @Column(nullable = false) — adds a NOT NULL constraint to the DB column
    // columnDefinition = "TEXT" — stores as TEXT type instead of VARCHAR(255)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    // nullable = true is the default — description is optional
    @Column(columnDefinition = "TEXT")
    private String description;

    // @Column(nullable = false) — completed must always have a value
    @Column(nullable = false)
    private Boolean completed;
}
