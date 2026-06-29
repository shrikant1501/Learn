package com.learnjava.todo.model;

// Role represents the set of permissions a user has in the system.
// Stored as a VARCHAR in the DB column via @Enumerated(EnumType.STRING) on User.
//
// WHY an enum and not a separate roles table?
// Each user has exactly ONE role and the roles are fixed at compile time.
// A simple enum column is type-safe, readable in SQL, and easy to query.
// A separate roles table (many-to-many) is better when roles are dynamic or
// a user can hold multiple roles simultaneously — that is out of scope here.
//
// WHY EnumType.STRING and not ORDINAL?
// ORDINAL stores 0, 1, 2 — fragile: adding a value in the middle shifts all
// existing ordinals and silently corrupts your data.
// STRING stores "USER", "ADMIN" — safe: adding new values never affects existing rows.
// ALWAYS use EnumType.STRING in production.
public enum Role {

    // USER: the default role assigned to every newly registered user.
    // Can: read all todos, create todos, update todos.
    USER,

    // ADMIN: elevated role. Must be set manually (e.g., via SQL or a future admin endpoint).
    // Can: everything USER can do, PLUS delete todos.
    ADMIN
}
