package com.learnjava.todo.service;

import com.learnjava.todo.dto.request.UpdateTodoRequest;
import com.learnjava.todo.dto.response.TodoResponse;
import com.learnjava.todo.model.Todo;
import com.learnjava.todo.repository.TodoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// ============================================================================
// TodoCacheTest — proves @Cacheable and @CacheEvict actually work
//
// WHY @SpringBootTest here (not @ExtendWith(MockitoExtension.class))?
// @Cacheable and @CacheEvict are implemented using Spring AOP proxies.
// The proxy wraps the real TodoServiceImpl bean and intercepts method calls
// to check/populate/evict the cache BEFORE and AFTER the method executes.
//
// @ExtendWith(MockitoExtension.class) creates a plain Java object — no proxy,
// no Spring context, no caching. @Cacheable annotations would be invisible.
//
// @SpringBootTest loads the full Spring ApplicationContext:
//   - TodoServiceImpl bean is wrapped in a caching AOP proxy
//   - CacheManager (Caffeine) is initialized
//   - @MockBean replaces TodoRepository with a Mockito mock (no real DB)
//   - We test the BEHAVIOUR of the cache, not the business logic
//
// This is a common interview question:
//   "Why can't you test @Cacheable with Mockito alone?"
//   Answer: because @Cacheable is an AOP concern — it only works when
//   Spring's proxy infrastructure wraps the bean.
// ============================================================================
@SpringBootTest
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-unit-tests-only-32chars",
        "jwt.expiration=900000",
        "jwt.refresh-token-expiration=604800000"
})
class TodoCacheTest {

    // We inject the REAL TodoService from the Spring context.
    // It is actually a CGLIB proxy that wraps TodoServiceImpl.
    // Method calls go through the proxy first (cache check) then the real method.
    @Autowired
    private TodoService todoService;

    // @MockBean replaces the real TodoRepository with a Mockito mock in the context.
    // This means our test does NOT need a database — the mock controls all responses.
    // We can then verify exactly how many times the mock was called.
    @MockBean
    private TodoRepository todoRepository;

    // We also need to mock these beans that are part of the full context
    @MockBean
    private com.learnjava.todo.security.JwtService jwtService;

    // CacheManager lets us manually clear the cache between tests,
    // ensuring each test starts with a clean (empty) cache state.
    @Autowired
    private CacheManager cacheManager;

    private Todo sampleTodo;

    @BeforeEach
    void setUp() {
        // Clear the "todos" cache before every test method.
        // Without this, a cache entry from one test pollutes the next test.
        // e.g., test 1 caches id=1 → test 2 calls getTodoById(1) → gets cached value
        // → verify(repository, times(1)) fails because it was called 0 times!
        var cache = cacheManager.getCache("todos");
        if (cache != null) cache.clear();

        sampleTodo = Todo.builder()
                .id(1L)
                .title("Learn Caching")
                .completed(false)
                .build();
    }

    // -----------------------------------------------------------------------
    // @Cacheable — cache HIT proof
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getTodoById: repository called once; second call served from cache")
    void getTodoById_secondCallServedFromCache() {
        // Arrange: repository returns the todo when asked
        when(todoRepository.findById(1L)).thenReturn(Optional.of(sampleTodo));

        // Act: call the service TWICE with the same id
        Optional<TodoResponse> first  = todoService.getTodoById(1L);
        Optional<TodoResponse> second = todoService.getTodoById(1L);

        // Assert: both calls returned the same result
        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(first.get().getTitle()).isEqualTo("Learn Caching");
        assertThat(second.get().getTitle()).isEqualTo("Learn Caching");

        // THE KEY ASSERTION: repository was only called ONCE despite two service calls.
        // The second call was served entirely from the Caffeine cache.
        // If @Cacheable were not working, this would fail with "wanted 1 time, but was 2".
        verify(todoRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("getTodoById: different ids are cached independently")
    void getTodoById_differentIds_cachedIndependently() {
        Todo todo2 = Todo.builder().id(2L).title("Second todo").completed(true).build();

        when(todoRepository.findById(1L)).thenReturn(Optional.of(sampleTodo));
        when(todoRepository.findById(2L)).thenReturn(Optional.of(todo2));

        // Two calls for id=1, two calls for id=2
        todoService.getTodoById(1L);
        todoService.getTodoById(1L); // cache hit for key 1
        todoService.getTodoById(2L);
        todoService.getTodoById(2L); // cache hit for key 2

        // Each id was loaded from DB exactly once — the other calls hit their own cache entry
        verify(todoRepository, times(1)).findById(1L);
        verify(todoRepository, times(1)).findById(2L);
    }

    @Test
    @DisplayName("getTodoById: caches Optional.empty() for non-existent ids")
    void getTodoById_notFound_cachesSentinelValue() {
        // Repository returns empty for id=99
        when(todoRepository.findById(99L)).thenReturn(Optional.empty());

        // Call twice
        Optional<TodoResponse> first  = todoService.getTodoById(99L);
        Optional<TodoResponse> second = todoService.getTodoById(99L);

        // Both return empty
        assertThat(first).isEmpty();
        assertThat(second).isEmpty();

        // DB was only queried once — even "not found" results are cached.
        // This prevents a "cache miss storm" for non-existent ids.
        verify(todoRepository, times(1)).findById(99L);
    }

    // -----------------------------------------------------------------------
    // @CacheEvict on updateTodo — stale data prevention proof
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("updateTodo: evicts cache entry so next getTodoById reloads from DB")
    void updateTodo_evictsCacheEntry_nextGetReloadsFromDb() {
        // Arrange: first load caches the OLD todo
        when(todoRepository.findById(1L)).thenReturn(Optional.of(sampleTodo));
        todoService.getTodoById(1L); // loads and caches

        // Now simulate an update — repository returns the updated todo on the next findById
        Todo updatedTodo = Todo.builder().id(1L).title("Updated title").completed(true).build();
        when(todoRepository.findById(1L)).thenReturn(Optional.of(updatedTodo));
        when(todoRepository.save(sampleTodo)).thenReturn(updatedTodo);

        UpdateTodoRequest updateRequest = UpdateTodoRequest.builder()
                .title("Updated title").completed(true).build();

        // Act: update evicts the cache entry for id=1
        todoService.updateTodo(1L, updateRequest);

        // Act: get the todo again — cache was evicted so this is a MISS, DB is called
        Optional<TodoResponse> afterUpdate = todoService.getTodoById(1L);

        // Assert: we got fresh data, not the stale cached value
        assertThat(afterUpdate).isPresent();
        assertThat(afterUpdate.get().getTitle()).isEqualTo("Updated title");

        // findById is called 3 times total:
        //   1. getTodoById(1L)     → cache MISS, loads from DB, caches result
        //   2. updateTodo(1L, ...) → internally calls findById to load the entity to mutate
        //   3. getTodoById(1L)     → cache MISS (evicted), reloads from DB
        //
        // If @CacheEvict were NOT working, call #3 would return the stale cached value
        // and findById would only be called 2 times (not 3). The title would still be
        // "Learn Caching" instead of "Updated title", proving the eviction works.
        verify(todoRepository, times(3)).findById(1L);
    }

    // -----------------------------------------------------------------------
    // @CacheEvict on deleteTodo — stale entry removal proof
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("deleteTodo: evicts cache entry so deleted todo is not served from cache")
    void deleteTodo_evictsCacheEntry() {
        // Arrange: cache the todo first
        when(todoRepository.findById(1L)).thenReturn(Optional.of(sampleTodo));
        when(todoRepository.existsById(1L)).thenReturn(true);
        todoService.getTodoById(1L); // caches id=1

        // Act: delete the todo — should evict the cache entry
        todoService.deleteTodo(1L);

        // After deletion, repository returns empty (the todo is gone)
        when(todoRepository.findById(1L)).thenReturn(Optional.empty());

        // Get the (now-deleted) todo — should be a cache MISS and return empty
        Optional<TodoResponse> result = todoService.getTodoById(1L);

        assertThat(result).isEmpty();

        // If @CacheEvict were not working, result would be the stale cached todo,
        // and findById would be called 0 times (served entirely from stale cache).
        // Instead: 1st call cached it, 2nd call is a miss after eviction.
        verify(todoRepository, times(2)).findById(1L);
    }
}
