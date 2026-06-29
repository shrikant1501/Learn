package com.learnjava.todo.repository;

import com.learnjava.todo.model.Todo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface TodoRepository extends JpaRepository<Todo, Long>,
        JpaSpecificationExecutor<Todo> {
    // JpaRepository<Todo, Long>       → gives us findAll, findById, save, deleteById etc.
    // JpaSpecificationExecutor<Todo>  → gives us findAll(Specification, Pageable)
    //                                   which is what pagination + filtering needs.
    //
    // Spring Data generates an implementation covering BOTH interfaces at runtime.
    // We write zero SQL — the Specification we build in TodoSpecification
    // is translated to a WHERE clause by Hibernate automatically.
}
