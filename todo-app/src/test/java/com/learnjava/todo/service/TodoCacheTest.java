package com.learnjava.todo.service;

import com.learnjava.todo.dto.request.UpdateTodoRequest;
import com.learnjava.todo.dto.response.TodoResponse;
import com.learnjava.todo.model.Role;
import com.learnjava.todo.model.Todo;
import com.learnjava.todo.model.User;
import com.learnjava.todo.repository.TodoRepository;
import com.learnjava.todo.security.SecurityUtil;
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

    // Mock SecurityUtil so TodoServiceImpl doesn't try to read from an empty SecurityContext.
    // All cache tests run as a USER — isAdmin()=false, getCurrentUser()=cacheTestUser.
    @MockBean
    private SecurityUtil securityUtil;

    @MockBean
    private com.learnjava.todo.security.JwtService jwtService;

    // CacheManager lets us manually clear the cache between tests,
    // ensuring each test starts with a clean (empty) cache state.
    @Autowired
    private CacheManager cacheManager;

    private Todo sampleTodo;
    private User cacheTestUser;

    @BeforeEach
    void setUp() {
        cacheTestUser = User.builder().id(1L).username("cache-user").role(Role.USER).build();
        // All cache tests run as a regular USER who owns the todos
        when(securityUtil.isAdmin()).thenReturn(false);
        when(securityUtil.getCurrentUser()).thenReturn(cacheTestUser);

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
        // USER role: service calls findByIdAndOwner
        when(todoRepository.findByIdAndOwner(1L, cacheTestUser)).thenReturn(Optional.of(sampleTodo));

        Optional<TodoResponse> first  = todoService.getTodoById(1L);
        Optional<TodoResponse> second = todoService.getTodoById(1L);

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(first.get().getTitle()).isEqualTo("Learn Caching");
        assertThat(second.get().getTitle()).isEqualTo("Learn Caching");

        // THE KEY ASSERTION: repository called only ONCE — second call served from cache.
        verify(todoRepository, times(1)).findByIdAndOwner(1L, cacheTestUser);
    }

    @Test
    @DisplayName("getTodoById: different ids are cached independently")
    void getTodoById_differentIds_cachedIndependently() {
        Todo todo2 = Todo.builder().id(2L).title("Second todo").completed(true).build();

        when(todoRepository.findByIdAndOwner(1L, cacheTestUser)).thenReturn(Optional.of(sampleTodo));
        when(todoRepository.findByIdAndOwner(2L, cacheTestUser)).thenReturn(Optional.of(todo2));

        todoService.getTodoById(1L);
        todoService.getTodoById(1L); // cache hit for key 1
        todoService.getTodoById(2L);
        todoService.getTodoById(2L); // cache hit for key 2

        verify(todoRepository, times(1)).findByIdAndOwner(1L, cacheTestUser);
        verify(todoRepository, times(1)).findByIdAndOwner(2L, cacheTestUser);
    }

    @Test
    @DisplayName("getTodoById: caches Optional.empty() for non-existent ids")
    void getTodoById_notFound_cachesSentinelValue() {
        when(todoRepository.findByIdAndOwner(99L, cacheTestUser)).thenReturn(Optional.empty());

        Optional<TodoResponse> first  = todoService.getTodoById(99L);
        Optional<TodoResponse> second = todoService.getTodoById(99L);

        assertThat(first).isEmpty();
        assertThat(second).isEmpty();
        verify(todoRepository, times(1)).findByIdAndOwner(99L, cacheTestUser);
    }

    // -----------------------------------------------------------------------
    // @CacheEvict on updateTodo
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("updateTodo: evicts cache entry so next getTodoById reloads from DB")
    void updateTodo_evictsCacheEntry_nextGetReloadsFromDb() {
        // Step 1: first load — caches the OLD todo
        when(todoRepository.findByIdAndOwner(1L, cacheTestUser)).thenReturn(Optional.of(sampleTodo));
        todoService.getTodoById(1L); // loads and caches

        // Step 2: simulate an update
        Todo updatedTodo = Todo.builder().id(1L).title("Updated title").completed(true)
                .owner(cacheTestUser).build();
        when(todoRepository.findByIdAndOwner(1L, cacheTestUser)).thenReturn(Optional.of(updatedTodo));
        when(todoRepository.save(sampleTodo)).thenReturn(updatedTodo);

        UpdateTodoRequest updateRequest = UpdateTodoRequest.builder()
                .title("Updated title").completed(true).build();

        // Step 3: update evicts the cache
        todoService.updateTodo(1L, updateRequest);

        // Step 4: getTodoById — cache was evicted, loads fresh from DB
        Optional<TodoResponse> afterUpdate = todoService.getTodoById(1L);

        assertThat(afterUpdate).isPresent();
        assertThat(afterUpdate.get().getTitle()).isEqualTo("Updated title");

        // findByIdAndOwner called 3 times:
        //   1. getTodoById → cache MISS, loads, caches
        //   2. updateTodo  → loads entity to mutate
        //   3. getTodoById → cache MISS (evicted), reloads
        verify(todoRepository, times(3)).findByIdAndOwner(1L, cacheTestUser);
    }

    // -----------------------------------------------------------------------
    // @CacheEvict on deleteTodo
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("deleteTodo: evicts cache entry so deleted todo is not served from cache")
    void deleteTodo_evictsCacheEntry() {
        // Step 1: cache the todo (USER role uses findByIdAndOwner)
        when(todoRepository.findByIdAndOwner(1L, cacheTestUser)).thenReturn(Optional.of(sampleTodo));
        todoService.getTodoById(1L); // caches id=1

        // Step 2: delete — USER role: findByIdAndOwner then delete(todo)
        when(todoRepository.findByIdAndOwner(1L, cacheTestUser)).thenReturn(Optional.of(sampleTodo));
        todoService.deleteTodo(1L); // evicts id=1 from cache

        // Step 3: after deletion repository returns empty
        when(todoRepository.findByIdAndOwner(1L, cacheTestUser)).thenReturn(Optional.empty());

        Optional<TodoResponse> result = todoService.getTodoById(1L);

        assertThat(result).isEmpty();
        // findByIdAndOwner called 3 times: cache miss, delete lookup, post-eviction miss
        verify(todoRepository, times(3)).findByIdAndOwner(1L, cacheTestUser);
    }
}
