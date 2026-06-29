package com.learnjava.todo.service;

import com.learnjava.todo.dto.request.CreateTodoRequest;
import com.learnjava.todo.dto.request.UpdateTodoRequest;
import com.learnjava.todo.dto.response.TodoResponse;
import com.learnjava.todo.model.Todo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

// @Mapper — marks this interface as a MapStruct mapper.
// MapStruct's annotation processor reads this interface at compile time
// and generates a class called TodoMapperImpl in target/generated-sources.
//
// componentModel = "spring":
//   Tells MapStruct to annotate the generated class with @Component.
//   Spring then picks it up and manages it as a bean — injectable via constructor injection.
//   Without this, Spring doesn't know about the generated class.
//
// nullValuePropertyMappingStrategy = IGNORE:
//   When a source field is null, don't overwrite the target field.
//   Critical for the updateModel method — if a client sends null for a field,
//   we keep the existing DB value instead of overwriting it with null.
@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface TodoMapper {

    // =========================================================================
    // CreateTodoRequest → Todo (new entity, no ID yet)
    // =========================================================================

    // @Mapping(target = "id", ignore = true):
    //   CreateTodoRequest has no id field. Without this, MapStruct would warn
    //   about an unmapped target property. We explicitly say: "id is intentionally
    //   left alone — the database assigns it on INSERT."
    //
    // @Mapping(target = "completed", defaultValue = "false"):
    //   If the client sends null for completed, default to false.
    //   MapStruct sets this default only when the source value is null.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "completed", defaultValue = "false")
    Todo toModel(CreateTodoRequest request);

    // =========================================================================
    // UpdateTodoRequest → existing Todo (@MappingTarget — update in place)
    // =========================================================================

    // @MappingTarget Todo todo — this is the KEY pattern for updates.
    //
    // Instead of creating a NEW Todo object, MapStruct updates the EXISTING
    // Todo entity that was loaded from the database. This means:
    //   1. Fields from UpdateTodoRequest overwrite fields on the existing entity
    //   2. Fields NOT present in UpdateTodoRequest are left untouched (e.g., id)
    //   3. When we add auditing (Phase 12), createdAt is preserved automatically
    //
    // The method returns void because the target is mutated in place.
    // The caller (TodoServiceImpl) loads the entity from DB, passes it here,
    // then saves it — Hibernate's dirty-checking does the rest.
    //
    // @Mapping(target = "id", ignore = true):
    //   The id lives in the URL path, not in the request body. We must NOT
    //   let UpdateTodoRequest accidentally overwrite the entity's id.
    @Mapping(target = "id", ignore = true)
    void updateModel(@MappingTarget Todo todo, UpdateTodoRequest request);

    // =========================================================================
    // Todo → TodoResponse (outbound)
    // =========================================================================

    // All fields (id, title, description, completed) match by name between
    // Todo and TodoResponse, so no @Mapping annotations are needed here.
    // MapStruct generates: return TodoResponse.builder()
    //                              .id(todo.getId()) ... .build();
    TodoResponse toResponse(Todo todo);
}
