package com.learnjava.todo.service;

import com.learnjava.todo.dto.request.CreateTodoRequest;
import com.learnjava.todo.dto.request.UpdateTodoRequest;
import com.learnjava.todo.dto.response.TodoResponse;
import com.learnjava.todo.model.Todo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// MapStruct generates a class called TodoMapperImpl.
// We test the REAL generated implementation — no Spring context, no mocks.
// MapStruct's generated code is plain Java, so we just instantiate it with new.
//
// Why test the generated code?
//   1. Verifies our @Mapping annotations are correct (defaultValue, ignore, etc.)
//   2. Catches mistakes if we add a new field and forget to map it
//   3. Fast — no Spring context startup, runs in milliseconds
class TodoMapperTest {

    // Mappers generated with componentModel = "spring" also have a default no-arg
    // constructor so they can be instantiated directly in tests without Spring.
    // Mappers.getMapper() is another option (MapStruct's factory), but new is simpler.
    private TodoMapper todoMapper;

    @BeforeEach
    void setUp() {
        // TodoMapperImpl is the class MapStruct generates from our TodoMapper interface.
        // It lives in target/generated-sources/annotations/ after compilation.
        // You can open it there to see exactly what MapStruct wrote.
        todoMapper = new TodoMapperImpl();
    }

    // -----------------------------------------------------------------------
    // toModel(CreateTodoRequest) — CREATE mapping
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("toModel: maps all fields from CreateTodoRequest to Todo")
    void toModel_create_mapsAllFields() {
        CreateTodoRequest request = CreateTodoRequest.builder()
                .title("Buy milk")
                .description("Full fat")
                .completed(false)
                .build();

        Todo result = todoMapper.toModel(request);

        // id must be null — @Mapping(target = "id", ignore = true) on the interface
        assertThat(result.getId()).isNull();
        assertThat(result.getTitle()).isEqualTo("Buy milk");
        assertThat(result.getDescription()).isEqualTo("Full fat");
        assertThat(result.getCompleted()).isFalse();
    }

    @Test
    @DisplayName("toModel: defaults completed to false when null in request")
    void toModel_create_defaultsCompletedToFalse() {
        // completed not set — @Mapping(target = "completed", defaultValue = "false") kicks in
        CreateTodoRequest request = CreateTodoRequest.builder()
                .title("Some task")
                .build();

        Todo result = todoMapper.toModel(request);

        assertThat(result.getCompleted()).isFalse();
    }

    // -----------------------------------------------------------------------
    // updateModel(@MappingTarget Todo, UpdateTodoRequest) — UPDATE IN PLACE
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("updateModel: overwrites Todo fields with values from UpdateTodoRequest")
    void updateModel_overwritesExistingFields() {
        // Simulate an entity already loaded from the database
        Todo existingTodo = Todo.builder()
                .id(5L)
                .title("Old title")
                .description("Old desc")
                .completed(false)
                .build();

        UpdateTodoRequest request = UpdateTodoRequest.builder()
                .title("New title")
                .description("New desc")
                .completed(true)
                .build();

        todoMapper.updateModel(existingTodo, request);

        // Fields from the request should be applied
        assertThat(existingTodo.getTitle()).isEqualTo("New title");
        assertThat(existingTodo.getDescription()).isEqualTo("New desc");
        assertThat(existingTodo.getCompleted()).isTrue();
        // id must be preserved — @Mapping(target = "id", ignore = true)
        assertThat(existingTodo.getId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("updateModel: preserves existing field when request field is null (IGNORE strategy)")
    void updateModel_nullField_preservesExistingValue() {
        // Simulate an entity already in DB with a description
        Todo existingTodo = Todo.builder()
                .id(3L)
                .title("Keep me")
                .description("Existing description")
                .completed(false)
                .build();

        // Request only updates title — description is null
        UpdateTodoRequest request = UpdateTodoRequest.builder()
                .title("Updated title")
                .completed(true)
                .build(); // description intentionally left null

        todoMapper.updateModel(existingTodo, request);

        assertThat(existingTodo.getTitle()).isEqualTo("Updated title");
        // nullValuePropertyMappingStrategy = IGNORE means null description
        // in the request does NOT overwrite the existing description
        assertThat(existingTodo.getDescription()).isEqualTo("Existing description");
        assertThat(existingTodo.getCompleted()).isTrue();
    }

    // -----------------------------------------------------------------------
    // toResponse(Todo) — OUTBOUND mapping
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("toResponse: maps all fields from Todo to TodoResponse")
    void toResponse_mapsAllFields() {
        Todo todo = Todo.builder()
                .id(1L)
                .title("Learn JPA")
                .description("Entities and repos")
                .completed(true)
                .build();

        TodoResponse result = todoMapper.toResponse(todo);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Learn JPA");
        assertThat(result.getDescription()).isEqualTo("Entities and repos");
        assertThat(result.getCompleted()).isTrue();
    }

    @Test
    @DisplayName("toResponse: maps null description to null in response")
    void toResponse_nullDescription_mapsToNull() {
        Todo todo = Todo.builder()
                .id(2L)
                .title("No desc")
                .completed(false)
                .build(); // no description

        TodoResponse result = todoMapper.toResponse(todo);

        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getTitle()).isEqualTo("No desc");
        assertThat(result.getDescription()).isNull();
    }
}
