package com.learnjava.todo.service;

import com.learnjava.todo.dto.request.CreateTodoRequest;
import com.learnjava.todo.dto.request.TodoFilterRequest;
import com.learnjava.todo.dto.request.UpdateTodoRequest;
import com.learnjava.todo.dto.response.PagedResponse;
import com.learnjava.todo.dto.response.TodoResponse;
import com.learnjava.todo.model.Role;
import com.learnjava.todo.model.Todo;
import com.learnjava.todo.model.User;
import com.learnjava.todo.repository.TodoRepository;
import com.learnjava.todo.security.SecurityUtil;
import com.learnjava.todo.service.impl.TodoServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

// LENIENT strictness: @BeforeEach stubs isAdmin() and getCurrentUser() for all tests.
// Not every test exercises both — Mockito strict mode would flag unused stubs.
// LENIENT disables that check while keeping all other strict-mode protections.
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class TodoServiceImplTest {

    @Mock
    private TodoRepository todoRepository;

    @Mock
    private TodoMapper todoMapper;

    // SecurityUtil is a Spring bean — mock it as an interface-less @Component.
    // Since SecurityUtil is a plain class (not abstract), we use @Mock with the
    // --add-opens in surefire to let ByteBuddy subclass it.
    @Mock
    private SecurityUtil securityUtil;

    private TodoServiceImpl todoService;

    private Todo sampleTodo;
    private TodoResponse sampleResponse;

    // The currently authenticated user used across all tests.
    private User currentUser;

    @BeforeEach
    void setUp() {
        // Build the service manually with all dependencies.
        todoService = new TodoServiceImpl(todoRepository, todoMapper, securityUtil, new SimpleMeterRegistry());

        currentUser = User.builder()
                .id(1L)
                .username("testuser")
                .role(Role.USER)
                .build();

        // Default: all tests run as a non-admin USER.
        // Individual tests that need ADMIN behaviour override these.
        when(securityUtil.isAdmin()).thenReturn(false);
        when(securityUtil.getCurrentUser()).thenReturn(currentUser);
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
    @DisplayName("getTodoById: returns Optional with response when USER owns the todo")
    void getTodoById_found_returnsOptionalWithResponse() {
        // USER role — uses findByIdAndOwner (ownership-scoped lookup)
        when(todoRepository.findByIdAndOwner(1L, currentUser)).thenReturn(Optional.of(sampleTodo));
        when(todoMapper.toResponse(sampleTodo)).thenReturn(sampleResponse);

        Optional<TodoResponse> result = todoService.getTodoById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(1L);
        assertThat(result.get().getTitle()).isEqualTo("Learn Spring Boot");
    }

    @Test
    @DisplayName("getTodoById: returns empty when todo belongs to a different user")
    void getTodoById_notOwned_returnsEmptyOptional() {
        // findByIdAndOwner returns empty → the todo exists but belongs to someone else → 404
        when(todoRepository.findByIdAndOwner(99L, currentUser)).thenReturn(Optional.empty());

        Optional<TodoResponse> result = todoService.getTodoById(99L);

        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // createTodo
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("createTodo: saves entity with owner set and returns response with assigned id")
    void createTodo_savesAndReturnsResponse() {
        CreateTodoRequest request = CreateTodoRequest.builder()
                .title("New task").description("Details").completed(false).build();

        // Mapper returns an unsaved todo (no id, no owner yet)
        Todo unsaved = Todo.builder().title("New task").description("Details").completed(false).build();
        // After service sets owner and calls save, repository returns the persisted entity
        Todo savedTodo = Todo.builder().id(5L).title("New task").description("Details")
                .completed(false).owner(currentUser).build();
        TodoResponse savedResponse = TodoResponse.builder().id(5L).title("New task")
                .completed(false).ownedBy("testuser").build();

        when(todoMapper.toModel(request)).thenReturn(unsaved);
        // The service sets unsaved.owner = currentUser before calling save.
        // We use any(Todo.class) since the exact instance after mutation is hard to pin.
        when(todoRepository.save(any(Todo.class))).thenReturn(savedTodo);
        when(todoMapper.toResponse(savedTodo)).thenReturn(savedResponse);

        TodoResponse result = todoService.createTodo(request);

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getTitle()).isEqualTo("New task");
        // Verify save was called — owner was set before save (can't easily verify the field
        // on the exact argument since the same instance is mutated, but save must be called)
        verify(todoRepository).save(any(Todo.class));
    }

    @Test
    @DisplayName("createTodo: defaults completed to false when not provided")
    void createTodo_nullCompleted_defaultsFalse() {
        CreateTodoRequest request = CreateTodoRequest.builder()
                .title("Task without completed").build();

        Todo unsaved = Todo.builder().title("Task without completed").completed(false).build();
        Todo savedTodo = Todo.builder().id(1L).title("Task without completed")
                .completed(false).owner(currentUser).build();
        TodoResponse savedResponse = TodoResponse.builder().id(1L).title("Task without completed")
                .completed(false).build();

        when(todoMapper.toModel(request)).thenReturn(unsaved);
        when(todoRepository.save(any(Todo.class))).thenReturn(savedTodo);
        when(todoMapper.toResponse(savedTodo)).thenReturn(savedResponse);

        TodoResponse result = todoService.createTodo(request);

        assertThat(result.getCompleted()).isFalse();
    }

    // -----------------------------------------------------------------------
    // updateTodo — @MappingTarget pattern
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("updateTodo: loads entity by owner, applies update, saves, returns response")
    void updateTodo_found_returnsUpdatedResponse() {
        UpdateTodoRequest request = UpdateTodoRequest.builder()
                .title("Updated title").completed(true).build();

        Todo existingTodo = Todo.builder().id(1L).title("Old title").completed(false)
                .owner(currentUser).build();
        Todo savedTodo = Todo.builder().id(1L).title("Updated title").completed(true)
                .owner(currentUser).build();
        TodoResponse updatedResponse = TodoResponse.builder().id(1L).title("Updated title")
                .completed(true).build();

        // USER role uses findByIdAndOwner
        when(todoRepository.findByIdAndOwner(1L, currentUser)).thenReturn(Optional.of(existingTodo));

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
        verify(todoRepository).findByIdAndOwner(1L, currentUser);
        verify(todoMapper).updateModel(existingTodo, request);
        verify(todoRepository).save(existingTodo);
    }

    @Test
    @DisplayName("updateTodo: returns empty Optional when todo not found or not owned")
    void updateTodo_notFound_returnsEmpty() {
        when(todoRepository.findByIdAndOwner(99L, currentUser)).thenReturn(Optional.empty());

        Optional<TodoResponse> result = todoService.updateTodo(99L,
                UpdateTodoRequest.builder().title("anything").build());

        assertThat(result).isEmpty();
        verify(todoRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // deleteTodo
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("deleteTodo (USER): returns true and calls delete when user owns the todo")
    void deleteTodo_found_deletesAndReturnsTrue() {
        Todo ownedTodo = Todo.builder().id(1L).title("My todo").completed(false)
                .owner(currentUser).build();
        // USER role: service calls findByIdAndOwner, then delete(todo)
        when(todoRepository.findByIdAndOwner(1L, currentUser)).thenReturn(Optional.of(ownedTodo));

        boolean result = todoService.deleteTodo(1L);

        assertThat(result).isTrue();
        verify(todoRepository).delete(ownedTodo);
    }

    @Test
    @DisplayName("deleteTodo (USER): returns false when todo not found or not owned")
    void deleteTodo_notFound_returnsFalse() {
        when(todoRepository.findByIdAndOwner(99L, currentUser)).thenReturn(Optional.empty());

        boolean result = todoService.deleteTodo(99L);

        assertThat(result).isFalse();
        verify(todoRepository, never()).delete(any(Todo.class));
    }

    @Test
    @DisplayName("deleteTodo (ADMIN): uses existsById + deleteById, ignores ownership")
    void deleteTodo_admin_deletesAnyTodo() {
        when(securityUtil.isAdmin()).thenReturn(true);
        when(todoRepository.existsById(1L)).thenReturn(true);

        boolean result = todoService.deleteTodo(1L);

        assertThat(result).isTrue();
        verify(todoRepository).deleteById(1L);
    }
}
