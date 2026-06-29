# Phase 11 — MapStruct

## Goal
Replace the hand-written static `TodoMapper` utility class with a MapStruct interface.
MapStruct generates the implementation at compile time — zero boilerplate, zero reflection,
compile-time safety.

---

## What Changed

| File | Before | After |
|------|--------|-------|
| `TodoMapper.java` | `public final class` with static methods | `public interface` with `@Mapper` |
| `TodoServiceImpl.java` | `TodoMapper.staticMethod()` | injected `todoMapper.instanceMethod()` |
| `TodoMapperTest.java` | Tests static methods directly | Tests `new TodoMapperImpl()` directly |
| `TodoServiceImplTest.java` | No mapper mock needed | `@Mock TodoMapper todoMapper` injected |
| `pom.xml` | Lombok only in annotationProcessorPaths | Lombok + lombok-mapstruct-binding + mapstruct-processor |

---

## Key MapStruct Annotations Used

### `@Mapper(componentModel = "spring")`
Generates a `@Component` class — Spring manages it as a bean, injectable anywhere.

### `@Mapping(target = "id", ignore = true)`
Tells MapStruct: "don't try to set `id` on the target object".
Used on `toModel(CreateTodoRequest)` because the DB assigns the id, not the client.

### `@Mapping(target = "completed", defaultValue = "false")`
If `completed` is `null` in the source, use `false` as the default.
Preserves the null-safety logic from our old hand-written mapper.

### `@MappingTarget Todo todo` on `updateModel()`
The most important pattern — MapStruct mutates the **existing entity** instead of creating
a new one. Generated code:
```java
if (request.getTitle() != null) todo.setTitle(request.getTitle());
if (request.getDescription() != null) todo.setDescription(request.getDescription());
```
Notice: null fields in the request are SKIPPED (from `nullValuePropertyMappingStrategy = IGNORE`).

### `nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE`
Applied at the `@Mapper` level — affects all methods.
When a source field is null, don't overwrite the target field.
Critical for partial updates: if a client sends `{ "title": "New" }` without a description,
the existing description is preserved.

---

## Generated Code (Inspect in `target/generated-sources/annotations/`)

```java
@Component
public class TodoMapperImpl implements TodoMapper {

    @Override
    public Todo toModel(CreateTodoRequest request) {
        Todo.TodoBuilder todo = Todo.builder();
        if (request.getCompleted() != null) {
            todo.completed(request.getCompleted());
        } else {
            todo.completed(false);   // defaultValue = "false"
        }
        todo.title(request.getTitle());
        todo.description(request.getDescription());
        // id is not set — @Mapping(target = "id", ignore = true)
        return todo.build();
    }

    @Override
    public void updateModel(Todo todo, UpdateTodoRequest request) {
        // nullValuePropertyMappingStrategy = IGNORE generates null-checks
        if (request.getTitle() != null) todo.setTitle(request.getTitle());
        if (request.getDescription() != null) todo.setDescription(request.getDescription());
        if (request.getCompleted() != null) todo.setCompleted(request.getCompleted());
        // id is not set — @Mapping(target = "id", ignore = true)
    }

    @Override
    public TodoResponse toResponse(Todo todo) {
        return TodoResponse.builder()
                .id(todo.getId())
                .title(todo.getTitle())
                .description(todo.getDescription())
                .completed(todo.getCompleted())
                .build();
    }
}
```

---

## updateTodo Improvement — `@MappingTarget` vs. Old Approach

**Old approach (Phase 5–10):**
```java
// Creates a BRAND NEW Todo — discards anything not in UpdateTodoRequest
Todo updated = todoRepository.save(TodoMapper.toModel(id, request));
```
Problem: When Phase 12 adds `createdAt`, a new Todo object has no `createdAt` → it gets reset.

**New approach (Phase 11+):**
```java
// Loads existing entity from DB — applies only changed fields
return todoRepository.findById(id)
        .map(existingTodo -> {
            todoMapper.updateModel(existingTodo, request); // mutates in place
            return todoMapper.toResponse(todoRepository.save(existingTodo));
        });
```
Hibernate's dirty-checking detects only the changed fields → minimal SQL UPDATE.

---

## pom.xml — Annotation Processor Order

```
Lombok (runs 1st)   → generates getters/setters/builders
lombok-mapstruct-binding → coordination bridge
MapStruct (runs 2nd) → reads Lombok-generated code → generates mapper impl
```
If order is wrong, MapStruct sees classes without getters → generates broken code.

---

## Common Interview Questions

**Q: What is MapStruct and how does it differ from ModelMapper?**
MapStruct = compile-time code generation (annotation processor). Zero reflection at runtime.
ModelMapper = runtime reflection. Slower, harder to debug, fails silently on name mismatches.
MapStruct is preferred in production for performance and compile-time safety.

**Q: How does MapStruct know which fields to map?**
By name — `source.getTitle()` maps to `target.setTitle()` automatically.
For name differences: `@Mapping(source = "firstName", target = "name")`.
For ignored fields: `@Mapping(target = "id", ignore = true)`.

**Q: What is `@MappingTarget` and when do you use it?**
Updates an existing object instead of creating a new one.
Use it for PUT/PATCH endpoints — load the entity from DB, then apply changes on top.
This preserves fields not in the update request (like `createdAt`, `version`, etc.)

**Q: What does `nullValuePropertyMappingStrategy = IGNORE` do?**
When a source field is null, the corresponding target field is NOT overwritten.
Essential for partial updates (PATCH semantics) where client only sends changed fields.

**Q: Where does MapStruct put the generated code?**
`target/generated-sources/annotations/` — you can open it to see exactly what was generated.
This is regular Java code — readable, debuggable, no magic.

---

## Test Results
```
TodoControllerTest    : 13 tests ✅
TodoMapperTest        :  6 tests ✅  (now tests TodoMapperImpl directly)
TodoServiceImplTest   : 10 tests ✅  (now mocks TodoMapper interface)
TodoApplicationTest   :  1 test  ✅
─────────────────────────────────
Total                 : 30 tests ✅  BUILD SUCCESS
```
