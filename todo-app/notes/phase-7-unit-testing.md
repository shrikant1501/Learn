# Phase 7 — Unit Testing

## Results
```
TodoControllerTest    — 13 tests  ✅
TodoMapperTest        —  6 tests  ✅
TodoServiceImplTest   — 10 tests  ✅
TodoApplicationTest   —  1 test   ✅  (smoke test)
Total                 — 30 tests, 0 failures
```

---

## The Testing Pyramid

```
        /\
       /  \
      / E2E \         few, slow (full app + real browser)
     /--------\
    /Integration\     some, medium speed (@SpringBootTest)
   /------------\
  /  Unit Tests  \    many, fast, isolated  ← what we built
 /________________\
```

Most tests should be unit tests — fast, deterministic, tell you exactly what broke.

---

## Test Types We Wrote

### 1. TodoMapperTest — Pure Java
- No Spring, no Mockito, no annotations
- Calls static methods directly
- Runs in ~150ms total
- When to use: pure utility/conversion logic with no dependencies

### 2. TodoServiceImplTest — Mockito, no Spring
```java
@ExtendWith(MockitoExtension.class)   // activates Mockito annotations
class TodoServiceImplTest {
    @Mock
    private TodoRepository todoRepository;  // fake repository

    @InjectMocks
    private TodoServiceImpl todoService;    // real service, mocked dependency
}
```
- No database, no Spring context
- Tests business logic decisions in isolation
- When `todoRepository.findById(99L)` returns empty, does the service return empty?
- Runs in ~300ms total

### 3. TodoControllerTest — MockMvc + @WebMvcTest
```java
@WebMvcTest(TodoController.class)     // loads ONLY web layer
class TodoControllerTest {
    @Autowired MockMvc mockMvc;        // for sending fake HTTP requests
    @Autowired ObjectMapper objectMapper; // for converting objects to JSON
    @MockBean TodoService todoService;    // fake service registered as Spring bean
}
```
- No database, no real service, no server
- Tests HTTP contracts: status codes, JSON shape, headers, validation
- Runs in ~4s (loads partial Spring context — just web layer)

---

## Key Annotations

| Annotation | Scope | Use |
|---|---|---|
| `@ExtendWith(MockitoExtension.class)` | Class | Activates Mockito without Spring |
| `@Mock` | Field | Creates a Mockito mock (no Spring) |
| `@InjectMocks` | Field | Creates real instance, injects @Mocks |
| `@WebMvcTest(Foo.class)` | Class | Loads only web layer |
| `@MockBean` | Field | Creates mock AND registers as Spring bean |
| `@BeforeEach` | Method | Runs before every test — set up fixtures |
| `@Test` | Method | Marks a test method |
| `@DisplayName` | Method | Human-readable test name in reports |

---

## Mockito Core Patterns

```java
// STUB — configure what the mock returns
when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));
when(todoRepository.existsById(99L)).thenReturn(false);

// VERIFY — assert the mock was called correctly
verify(todoRepository).findAll();
verify(todoRepository).deleteById(1L);
verify(todoRepository, never()).save(any()); // never() = must NOT have been called

// ARGUMENT MATCHERS
any(Todo.class)     // any Todo instance
any()               // any object
eq(1L)              // exactly this value — use when mixing matchers with real values
```

**Rule:** If you use any matcher in a `when()` or `verify()`, ALL arguments must be matchers.
```java
// ❌ mixes real value and matcher
when(service.updateTodo(1L, any(UpdateTodoRequest.class)))

// ✅ use eq() to wrap the real value
when(service.updateTodo(eq(1L), any(UpdateTodoRequest.class)))
```

---

## MockMvc Core Patterns

```java
// GET request
mockMvc.perform(get("/api/v1/todos/1"))
       .andExpect(status().isOk())
       .andExpect(jsonPath("$.title", is("Buy milk")));

// POST with body
mockMvc.perform(post("/api/v1/todos")
               .contentType(MediaType.APPLICATION_JSON)
               .content(objectMapper.writeValueAsString(request)))
       .andExpect(status().isCreated())
       .andExpect(header().string("Location", containsString("/api/v1/todos/4")));

// DELETE
mockMvc.perform(delete("/api/v1/todos/1"))
       .andExpect(status().isNoContent());
```

## JsonPath Expressions

```
$          → root of the response
$.id       → top-level id field
$.title    → top-level title field
$[0].id    → first element of root array, id field
$          with hasSize(3) → array has 3 elements
$.errors.title → nested field
```

---

## @Mock vs @MockBean

| | `@Mock` | `@MockBean` |
|---|---|---|
| Framework | Mockito only | Spring + Mockito |
| Use with | `@ExtendWith(MockitoExtension.class)` | `@WebMvcTest` / `@SpringBootTest` |
| Registers as Spring bean | ❌ | ✅ |
| When to use | Pure unit tests (no Spring) | Tests that load Spring context |

---

## The AAA Pattern — Every Test Follows This

```java
@Test
void someTest() {
    // ARRANGE — set up the test data and stubs
    when(repository.findById(1L)).thenReturn(Optional.of(todo));

    // ACT — call the thing you're testing
    Optional<TodoResponse> result = service.getTodoById(1L);

    // ASSERT — verify the outcome
    assertThat(result).isPresent();
    assertThat(result.get().getTitle()).isEqualTo("Learn Spring Boot");
}
```

---

## Interview Questions & Answers

**Q: What is the difference between @Mock and @MockBean?**
`@Mock` is pure Mockito — creates a mock without any Spring involvement.
`@MockBean` creates a mock AND registers it in the Spring ApplicationContext.
Use `@Mock` in non-Spring tests. Use `@MockBean` when the test loads a Spring context.

**Q: What is @WebMvcTest and what does it load?**
Loads only the web layer: controllers, filters, `@ControllerAdvice`, `MockMvc`.
Does NOT load: services, repositories, JPA, the database.
Much faster than `@SpringBootTest` for testing HTTP contracts.

**Q: Why do we mock the service in controller tests?**
To test the controller in isolation. If a controller test fails, we know the bug is
in the controller (routing, serialization, validation, status codes) — not in the
service or database. Mocking replaces the service with a predictable double.

**Q: What does verify() do in Mockito?**
Asserts that a method was called on a mock — with specific arguments, a specific
number of times. `verify(repo).deleteById(1L)` fails if deleteById was never called,
called with a different argument, or called more than once.

**Q: What is the AAA pattern in testing?**
Arrange-Act-Assert. Set up test data → call the method under test → verify the result.
Every test should follow this structure for clarity and readability.

**Q: What is jsonPath and how does it work?**
JsonPath is an expression language for navigating JSON (like XPath for XML).
`$` = root. `$.title` = top-level title field. `$[0].id` = id of first array element.
Spring's MockMvc integrates JsonPath via `andExpect(jsonPath(...))`.

---

## Exercises
- [ ] Exercise 1: Add a test for `GET /api/v1/todos` that verifies the service is called via `verify()`
- [ ] Exercise 2: Add a `TodoServiceImplTest` case — what happens when `createTodo` is called with a title of 500 chars? (hint: validation is at the controller level — what does the service do?)
- [ ] Exercise 3: In `TodoControllerTest`, add `.andDo(print())` to any test to see the full request/response in the console. Import: `import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;`
- [ ] Exercise 4: Make one test deliberately fail by changing an expected value. Observe the error message. Then fix it back.

---

## Notes / Questions
_(Add your own notes here)_
