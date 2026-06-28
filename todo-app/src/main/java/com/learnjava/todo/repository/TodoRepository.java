package com.learnjava.todo.repository;

import com.learnjava.todo.model.Todo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// @Repository — marks this as a Spring bean for the data access layer.
// Also enables Spring's PersistenceExceptionTranslation:
// raw JPA/Hibernate exceptions are automatically translated into
// Spring's DataAccessException hierarchy — consistent exception handling.
@Repository
public interface TodoRepository extends JpaRepository<Todo, Long> {
    // JpaRepository<Todo, Long> means:
    //   Todo  = the entity type this repository manages
    //   Long  = the type of the entity's @Id field
    //
    // Spring Data JPA generates a full implementation at runtime with:
    //   findAll()           → SELECT * FROM todos
    //   findById(id)        → SELECT * FROM todos WHERE id = ?   (returns Optional<Todo>)
    //   save(todo)          → INSERT or UPDATE (checks if id is null)
    //   deleteById(id)      → DELETE FROM todos WHERE id = ?
    //   existsById(id)      → SELECT COUNT(*) > 0 WHERE id = ?
    //   count()             → SELECT COUNT(*) FROM todos
    //
    // No SQL written. No implementation class. Spring generates everything.
    // We add custom query methods here in future phases when we need filtering/search.
}
