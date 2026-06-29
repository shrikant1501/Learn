package com.learnjava.todo.service;

import com.learnjava.todo.dto.request.TodoFilterRequest;
import com.learnjava.todo.model.Todo;
import com.learnjava.todo.model.User;
import org.springframework.data.jpa.domain.Specification;

// Factory class that builds JPA Specification objects from filter parameters.
// Each static method returns one Specification (one WHERE clause fragment).
// Callers combine them with .and() to build the full query dynamically.
public class TodoSpecification {

    private TodoSpecification() {}

    // Filters by completed status: WHERE completed = ?
    public static Specification<Todo> hasCompleted(Boolean completed) {
        return (root, query, cb) ->
                cb.equal(root.get("completed"), completed);
    }

    // Searches title OR description for a keyword (case-insensitive):
    // WHERE LOWER(title) LIKE ? OR LOWER(description) LIKE ?
    public static Specification<Todo> hasSearchTerm(String search) {
        return (root, query, cb) -> {
            String pattern = "%" + search.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            );
        };
    }

    // Filters to only the given user's todos: WHERE user_id = ?
    // Used when the current user is a regular USER (not ADMIN).
    // ADMIN callers pass null for owner → this predicate is not added.
    //
    // Why use root.get("owner") instead of root.get("user_id")?
    // In JPA Criteria API, you navigate by ENTITY field name ("owner"), not by
    // the DB column name ("user_id"). JPA translates the join to the FK column.
    public static Specification<Todo> hasOwner(User owner) {
        return (root, query, cb) ->
                cb.equal(root.get("owner"), owner);
    }

    // Builds a combined Specification from a filter request and optional owner.
    // owner = null  → ADMIN caller, no ownership filter (sees all todos)
    // owner = User  → regular USER caller, restricted to their own todos only
    public static Specification<Todo> fromFilter(TodoFilterRequest filter, User owner) {
        Specification<Todo> spec = Specification.where(null);

        // Scope to current user's todos — skip for ADMIN (owner == null)
        if (owner != null) {
            spec = spec.and(hasOwner(owner));
        }

        if (filter.getCompleted() != null) {
            spec = spec.and(hasCompleted(filter.getCompleted()));
        }

        if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
            spec = spec.and(hasSearchTerm(filter.getSearch()));
        }

        return spec;
    }

    // Backward-compatible overload used by existing unit tests (no owner filter).
    // Delegates to the main method with owner = null (ADMIN behaviour).
    public static Specification<Todo> fromFilter(TodoFilterRequest filter) {
        return fromFilter(filter, null);
    }
}
