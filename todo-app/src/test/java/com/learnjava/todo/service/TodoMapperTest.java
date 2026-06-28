package com.learnjava.todo.service;

import com.learnjava.todo.dto.request.CreateTodoRequest;
import com.learnjava.todo.dto.request.UpdateTodoRequest;
import com.learnjava.todo.dto.response.TodoResponse;
import com.learnjava.todo.model.Todo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// No Spring annotations here — TodoMapper uses only static methods.
// We instantiate nothing, start nothing. These tests run in milliseconds.
class TodoMapperTest {

    // -----------------------------------------------------------------------
    // toModel(CreateTodoRequest)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("toModel: maps all fields from CreateTodoRequest to Todo")
    void toModel_create_mapsAllFields() {
        CreateTodoRequest request = CreateTodoRequest.builder()
                .title("Buy milk")
                .description("Full fat")
                .completed(false)
                .build();

        Todo result = TodoMapper.toModel(request);

        // id must be null — the DB assigns it, not the mapper
        assertThat(result.getId()).isNull();
        assertThat(result.getTitle()).isEqualTo("Buy milk");
        assertThat(result.getDescription()).isEqualTo("Full fat");
        assertThat(result.getCompleted()).isFalse();
    }

    @Test
    @DisplayName("toModel: defaults completed to false when null in request")
    void toModel_create_defaultsCompletedToFalse() {
        // completed not set → null in the request
        CreateTodoRequest request = CreateTodoRequest.builder()
                .title("Some task")
                .build();

        Todo result = TodoMapper.toModel(request);

        assertThat(result.getCompleted()).isFalse();
    }

    // -----------------------------------------------------------------------
    // toModel(Long id, UpdateTodoRequest)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("toModel: stamps URL-path id onto the Todo for updates")
    void toModel_update_stampsIdFromPath() {
        UpdateTodoRequest request = UpdateTodoRequest.builder()
                .title("Updated title")
                .description("Updated desc")
                .completed(true)
                .build();

        Todo result = TodoMapper.toModel(5L, request);

        // id must come from the method parameter, not from the request body
        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getTitle()).isEqualTo("Updated title");
        assertThat(result.getDescription()).isEqualTo("Updated desc");
        assertThat(result.getCompleted()).isTrue();
    }

    // -----------------------------------------------------------------------
    // toResponse(Todo)
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

        TodoResponse result = TodoMapper.toResponse(todo);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Learn JPA");
        assertThat(result.getDescription()).isEqualTo("Entities and repos");
        assertThat(result.getCompleted()).isTrue();
    }

    // -----------------------------------------------------------------------
    // toResponseList(List<Todo>)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("toResponseList: converts each Todo in list to TodoResponse")
    void toResponseList_convertsEachElement() {
        List<Todo> todos = List.of(
                Todo.builder().id(1L).title("First").completed(false).build(),
                Todo.builder().id(2L).title("Second").completed(true).build()
        );

        List<TodoResponse> result = TodoMapper.toResponseList(todos);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getTitle()).isEqualTo("First");
        assertThat(result.get(1).getId()).isEqualTo(2L);
        assertThat(result.get(1).getTitle()).isEqualTo("Second");
    }

    @Test
    @DisplayName("toResponseList: returns empty list when input is empty")
    void toResponseList_emptyInput_returnsEmptyList() {
        List<TodoResponse> result = TodoMapper.toResponseList(List.of());

        assertThat(result).isEmpty();
    }
}
