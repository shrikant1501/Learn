package com.learnjava.todo.service;

import com.learnjava.todo.dto.request.CreateTodoRequest;
import com.learnjava.todo.dto.request.TodoFilterRequest;
import com.learnjava.todo.dto.request.UpdateTodoRequest;
import com.learnjava.todo.dto.response.PagedResponse;
import com.learnjava.todo.dto.response.TodoResponse;
import com.learnjava.todo.model.Todo;
import com.learnjava.todo.repository.TodoRepository;
import com.learnjava.todo.service.impl.TodoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// @ExtendWith(MockitoExtension.class) — activates Mockito without Spring.
// No Spring context is loaded. No database is touched. Tests run in milliseconds.
@ExtendWith(MockitoExtension.class)
class TodoServiceImplTest {

    @Mock
    private TodoRepository todoRepository;

    // TodoMapper is now an interface — Mockito mocks it via JDK proxy (no ByteBuddy needed).
    // This is one of the benefits of using an interface: clean, fast mocking.
    @Mock
    private TodoMapper todoMapper;

    // @InjectMocks — Mockito creates a real TodoServiceImpl and injects both mocks above.
    // TodoServiceImpl now has two constructor args (repository + mapper), Mockito handles both.
    @InjectMocks
    private TodoServiceImpl todoService;

    private Todo sampleTodo;
    private TodoResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleTodo = Todo.builder()
                .id(1L)
                .title("Learn Spring Boot")
                .description("Phase 11")
                .completed(false)
                .build();

        sampleResponse = TodoResponse.builder()
                .id(1L)
                .title("Learn Spring Boot")
                .description("Phase 11")
                .completed(false)
                .build();
    }

    // -----------------------------------------------------------------------
    // getTodos
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getTodos: returns paged response with content when todos exist")
    void getTodos_returnsMappedPagedResponse() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Todo> mockPage = new PageImpl<>(List.of(sampleTodo), pageable, 1);

        when(todoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mockPage);
        // The mapper's toResponse is called for each Todo in the page content
        when(todoMapper.toResponse(sampleTodo)).thenReturn(sampleResponse);

        PagedResponse<TodoResponse> result = todoService.getTodos(new TodoFilterRequest(), pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Learn Spring Boot");
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.isLast()).isTrue();
    }

    @Test
    @DisplayName("getTodos: returns empty paged response when no todos exist")
    void getTodos_returnsEmptyPagedResponse() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Todo> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(todoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(emptyPage);

        PagedResponse<TodoResponse> result = todoService.getTodos(new TodoFilterRequest(), pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    // -----------------------------------------------------------------------
    // getTodoById
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getTodoById: returns Optional with response when todo exists")
    void getTodoById_found_returnsOptionalWithResponse() {
        when(todoRepository.findById(1L)).thenReturn(Optional.of(sampleTodo));
        when(todoMapper.toResponse(sampleTodo)).thenReturn(sampleResponse);

        Optional<TodoResponse> result = todoService.getTodoById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(1L);
        assertThat(result.get().getTitle()).isEqualTo("Learn Spring Boot");
    }

    @Test
    @DisplayName("getTodoById: returns empty Optional when todo does not exist")
    void getTodoById_notFound_returnsEmptyOptional() {
        when(todoRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<TodoResponse> result = todoService.getTodoById(99L);

        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // createTodo
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("createTodo: saves entity and returns response with assigned id")
    void createTodo_savesAndReturnsResponse() {
        CreateTodoRequest request = CreateTodoRequest.builder()
                .title("New task").description("Details").completed(false).build();

        Todo unsaved = Todo.builder().title("New task").description("Details").completed(false).build();
        Todo savedTodo = Todo.builder().id(5L).title("New task").description("Details").completed(false).build();
        TodoResponse savedResponse = TodoResponse.builder().id(5L).title("New task").completed(false).build();

        when(todoMapper.toModel(request)).thenReturn(unsaved);
        when(todoRepository.save(unsaved)).thenReturn(savedTodo);
        when(todoMapper.toResponse(savedTodo)).thenReturn(savedResponse);

        TodoResponse result = todoService.createTodo(request);

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getTitle()).isEqualTo("New task");
        verify(todoRepository).save(unsaved);
    }

    @Test
    @DisplayName("createTodo: defaults completed to false when not provided")
    void createTodo_nullCompleted_defaultsFalse() {
        CreateTodoRequest request = CreateTodoRequest.builder()
                .title("Task without completed").build();

        Todo unsaved = Todo.builder().title("Task without completed").completed(false).build();
        Todo savedTodo = Todo.builder().id(1L).title("Task without completed").completed(false).build();
        TodoResponse savedResponse = TodoResponse.builder().id(1L).title("Task without completed").completed(false).build();

        when(todoMapper.toModel(request)).thenReturn(unsaved);
        when(todoRepository.save(unsaved)).thenReturn(savedTodo);
        when(todoMapper.toResponse(savedTodo)).thenReturn(savedResponse);

        TodoResponse result = todoService.createTodo(request);

        assertThat(result.getCompleted()).isFalse();
    }

    // -----------------------------------------------------------------------
    // updateTodo — @MappingTarget pattern
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("updateTodo: loads existing entity, applies update, saves, returns response")
    void updateTodo_found_returnsUpdatedResponse() {
        UpdateTodoRequest request = UpdateTodoRequest.builder()
                .title("Updated title").completed(true).build();

        // Simulate the entity loaded from DB
        Todo existingTodo = Todo.builder().id(1L).title("Old title").completed(false).build();
        // After mapper.updateModel() mutates it, save returns it back
        Todo savedTodo = Todo.builder().id(1L).title("Updated title").completed(true).build();
        TodoResponse updatedResponse = TodoResponse.builder().id(1L).title("Updated title").completed(true).build();

        when(todoRepository.findById(1L)).thenReturn(Optional.of(existingTodo));

        // updateModel is void — we use doAnswer to simulate the mutation it performs
        doAnswer(invocation -> {
            Todo target = invocation.getArgument(0);
            target.setTitle("Updated title");
            target.setCompleted(true);
            return null;
        }).when(todoMapper).updateModel(existingTodo, request);

        when(todoRepository.save(existingTodo)).thenReturn(savedTodo);
        when(todoMapper.toResponse(savedTodo)).thenReturn(updatedResponse);

        Optional<TodoResponse> result = todoService.updateTodo(1L, request);

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Updated title");
        assertThat(result.get().getCompleted()).isTrue();
        // Verify the correct flow: findById → updateModel → save
        verify(todoRepository).findById(1L);
        verify(todoMapper).updateModel(existingTodo, request);
        verify(todoRepository).save(existingTodo);
    }

    @Test
    @DisplayName("updateTodo: returns empty Optional when todo does not exist")
    void updateTodo_notFound_returnsEmpty() {
        when(todoRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<TodoResponse> result = todoService.updateTodo(99L,
                UpdateTodoRequest.builder().title("anything").build());

        assertThat(result).isEmpty();
        // save() must never be called when the entity doesn't exist
        verify(todoRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // deleteTodo
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("deleteTodo: returns true and calls deleteById when todo exists")
    void deleteTodo_found_deletesAndReturnsTrue() {
        when(todoRepository.existsById(1L)).thenReturn(true);

        boolean result = todoService.deleteTodo(1L);

        assertThat(result).isTrue();
        verify(todoRepository).deleteById(1L);
    }

    @Test
    @DisplayName("deleteTodo: returns false and never calls deleteById when todo does not exist")
    void deleteTodo_notFound_returnsFalse() {
        when(todoRepository.existsById(99L)).thenReturn(false);

        boolean result = todoService.deleteTodo(99L);

        assertThat(result).isFalse();
        verify(todoRepository, never()).deleteById(any());
    }
}
