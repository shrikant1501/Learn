# Phase 4 — DTOs (Data Transfer Objects)

## Goal
- Separate Request and Response DTOs for precise API contracts
- Controller imports NO domain model — only DTOs
- Hand-written `TodoMapper` translating between layers
- Foundation laid for Phase 5 (JPA entities)

---

## The Three Problems DTOs Solve

### Problem 1: Leaking internal structure
Without DTOs, adding JPA fields to `Todo` (@Version, @CreatedDate, etc.)
automatically exposes them in your API. Clients start depending on them.
You can never remove them. Your DB schema becomes your public API contract.

### Problem 2: Clients send fields they shouldn't
Without DTOs, clients can POST `{"id": 999, "title": "hack"}`.
With `CreateTodoRequest` (no `id` field), it's structurally impossible.
The type system enforces the security — not a runtime check.

### Problem 3: Request shape ≠ Response shape
Create: client sends `{title, description, completed}` (no id)
Response: server sends `{id, title, description, completed}` (with id)
One class can't cleanly serve both purposes.

---

## The Three DTOs

```
CreateTodoRequest   ← POST body (no id — server assigns it)
UpdateTodoRequest   ← PUT body  (no id — comes from URL path)
TodoResponse        ← all responses (includes id for client reference)
```

### Why separate Create and Update requests?
Today they look the same. But they diverge in the future:
- Validation rules differ: `title` mandatory on create, maybe optional on update
- Possible fields differ: create might have `dueDate`, update might have `priority`
- Having one class forces `// only for create` comment hacks — a design smell

---

## The DTO → Model → DTO Flow

```
HTTP Request JSON
      ↓ Jackson deserializes
CreateTodoRequest (DTO)
      ↓ TodoMapper.toModel()
Todo (domain model, no ID yet)
      ↓ service assigns ID, stores in HashMap
Todo (domain model, with ID)
      ↓ TodoMapper.toResponse()
TodoResponse (DTO)
      ↓ Jackson serializes
HTTP Response JSON
```

The `Todo` model NEVER leaves the service layer.
The controller never imports `com.learnjava.todo.model.Todo`.

---

## TodoMapper — Key Design Decisions

```java
public final class TodoMapper {
    private TodoMapper() {}  // utility class — never instantiated

    // Request DTO → Model (for create — no ID yet)
    public static Todo toModel(CreateTodoRequest request) { ... }

    // Request DTO + URL id → Model (for update — ID comes from URL)
    public static Todo toModel(Long id, UpdateTodoRequest request) { ... }

    // Model → Response DTO (outbound)
    public static TodoResponse toResponse(Todo todo) { ... }

    // List<Model> → List<Response DTO> (using streams)
    public static List<TodoResponse> toResponseList(List<Todo> todos) {
        return todos.stream()
                .map(TodoMapper::toResponse)  // method reference
                .toList();                    // Java 16+ immutable list
    }
}
```

**Why static methods, not a Spring @Component?**
Mapping is pure data transformation — no external deps, no I/O, no state.
No benefit to Spring managing it. Static utility is simpler and honest.

**Why not put mapping in the service?**
Single Responsibility: service changes for business logic changes.
Mapper changes for DTO/model shape changes. Different reasons → different classes.

**Why the mapper doesn't set the ID on create?**
ID generation (currently AtomicLong, later DB) is the service's responsibility.
The mapper maps fields — it should not know about sequence/ID strategies.

---

## Lombok Annotation Choices

| Class | Annotations | Why |
|---|---|---|
| `CreateTodoRequest` | `@Data @Builder @NoArgsConstructor @AllArgsConstructor` | Jackson needs setters to deserialize |
| `UpdateTodoRequest` | same as above | same reason |
| `TodoResponse` | `@Getter @Builder` | immutable — no setters needed |
| `ErrorResponse` | `@Getter @Builder` | immutable — no setters needed |

**Rule:** Use the most restrictive annotations that satisfy requirements.
Response/error objects → immutable → `@Getter` only.
Request objects → mutable for Jackson → `@Data`.

---

## Clean Architecture Boundary (The Big Insight)

```
TodoController
  imports: CreateTodoRequest, UpdateTodoRequest, TodoResponse, TodoService
  DOES NOT import: Todo (the model)

TodoService (interface)
  speaks: CreateTodoRequest, UpdateTodoRequest, TodoResponse

TodoServiceImpl
  internally uses: Todo (domain model) + TodoMapper
  externally returns: TodoResponse (DTO)

Todo (model)
  completely hidden inside the service layer
  will become @Entity in Phase 5 — controller unaffected
```

The compiler enforces this boundary. If you accidentally import `Todo` in the
controller, it's a design violation visible at a glance in the import list.

---

## Files Created
- `dto/request/CreateTodoRequest.java` — POST body shape
- `dto/request/UpdateTodoRequest.java` — PUT body shape
- `dto/response/TodoResponse.java` — all response shape
- `service/TodoMapper.java` — converts between model and DTOs

## Files Modified
- `service/TodoService.java` — interface now uses DTOs (no Todo model)
- `service/impl/TodoServiceImpl.java` — uses mapper for all conversions
- `controller/TodoController.java` — imports no domain model, only DTOs

---

## Interview Questions & Answers

**Q: What is a DTO and why use it?**
Data Transfer Object — a plain object carrying data across a layer boundary.
Benefits: hides internal structure, controls what clients can send/receive,
allows request and response shapes to evolve independently, enables validation
rules at the boundary, prevents leaking DB internals.

**Q: What is the difference between a DTO and a domain model (entity)?**
Domain model = represents business concepts, may have JPA annotations, behavior.
DTO = pure data carrier for a specific API interaction, no behavior, no persistence.
They serve different masters: entity serves the DB, DTO serves the API.

**Q: Why should request DTOs and response DTOs be separate classes?**
Different purposes, different validation rules, different evolution paths.
Merging them creates ambiguity (what does null mean — don't send or don't update?)
and couples the create and update contracts unnecessarily.

**Q: What is a method reference (TodoMapper::toResponse)?**
Shorthand for `todo -> TodoMapper.toResponse(todo)`.
Reads as "apply the toResponse method from TodoMapper to each element."
Cleaner than a lambda when the lambda body is just a single method call.

**Q: Why use .toList() instead of .collect(Collectors.toList())?**
`.toList()` was introduced in Java 16. It returns an UNMODIFIABLE list —
more defensive than `Collectors.toList()` which returns a mutable ArrayList.
Prefer `.toList()` in modern Java (16+) when you don't need to mutate the result.

---

## Exercises
- [ ] Exercise 1: POST `{"id": 999, "title": "test", "completed": false}` — verify the response id is NOT 999
- [ ] Exercise 2: Open `TodoController.java` — confirm there is no import of `com.learnjava.todo.model.Todo`
- [ ] Exercise 3: Trace the flow for `POST /api/v1/todos` on paper: JSON in → which classes → JSON out
- [ ] Exercise 4: Add a `priority` field to `CreateTodoRequest` only (not UpdateTodoRequest, not TodoResponse). What does the compiler tell you to fix? Don't implement it — just observe the cascade.

---

## What Phase 5 Will Prove

In Phase 5, `Todo.java` will gain these annotations:
```java
@Entity
@Table(name = "todos")
public class Todo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version
    private Integer version;      // ← internal, never in TodoResponse
    @CreatedDate
    private LocalDateTime createdAt;  // ← internal, never in TodoResponse
    ...
}
```

The controller will not change a single line.
The service interface will not change a single line.
Only `TodoServiceImpl` will change (HashMap → JpaRepository).

That moment of zero ripple effect is what DTOs are for.

---

## Notes / Questions
_(Add your own notes here)_
