package com.learnjava.todo.service;

import com.learnjava.todo.dto.request.TodoFilterRequest;
import com.learnjava.todo.model.Todo;
import org.springframework.data.jpa.domain.Specification;

// A factory class that builds JPA Specification objects from filter parameters.
// Each static method returns one Specification (one WHERE clause fragment).
// The caller combines them with .and() to build the full query dynamically.
//
// Why Specification instead of derived query methods?
// Derived methods are static — findByCompletedAndTitleContaining() is fixed.
// With optional filters, you'd need a method for every combination:
//   findByCompleted()
//   findByTitleContaining()
//   findByCompletedAndTitleContaining()  ← explosion as filters grow
//
// Specification composes at runtime — you only add predicates for filters
// that are actually provided. Zero duplication.
public class TodoSpecification {

    private TodoSpecification() {}

    // Returns a Specification that filters by completed status.
    // A Specification is a functional interface: (root, query, criteriaBuilder) -> Predicate
    //   root          = the FROM clause (represents the Todo entity)
    //   query         = the full query being built
    //   criteriaBuilder = factory for building predicates (like a SQL expression builder)
    public static Specification<Todo> hasCompleted(Boolean completed) {
        return (root, query, cb) ->
                // cb.equal() generates: WHERE completed = ?
                cb.equal(root.get("completed"), completed);
    }

    // Returns a Specification that searches title OR description for a keyword.
    // cb.lower() makes the comparison case-insensitive.
    // cb.like() generates a LIKE clause.
    // cb.or() combines the two predicates with OR.
    public static Specification<Todo> hasSearchTerm(String search) {
        return (root, query, cb) -> {
            String pattern = "%" + search.toLowerCase() + "%";
            return cb.or(
                    // WHERE LOWER(title) LIKE '%keyword%'
                    cb.like(cb.lower(root.get("title")), pattern),
                    // OR LOWER(description) LIKE '%keyword%'
                    cb.like(cb.lower(root.get("description")), pattern)
            );
        };
    }

    // Builds a combined Specification from a filter request.
    // Only adds predicates for filters that are actually present.
    // Returns null if no filters are set — findAll(null, pageable) works correctly
    // in Spring Data (null Specification = no WHERE clause = return everything).
    public static Specification<Todo> fromFilter(TodoFilterRequest filter) {
        Specification<Todo> spec = Specification.where(null); // start with no conditions

        if (filter.getCompleted() != null) {
            // .and() appends: AND completed = ?
            spec = spec.and(hasCompleted(filter.getCompleted()));
        }

        if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
            // .and() appends: AND (LOWER(title) LIKE ? OR LOWER(description) LIKE ?)
            spec = spec.and(hasSearchTerm(filter.getSearch()));
        }

        return spec;
    }
}
