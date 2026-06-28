# Phase 2 — Complete CRUD Operations

## Goal
- Full in-memory CRUD API (5 endpoints)
- Correct HTTP status codes for every scenario
- `HashMap` as simulated data store
- `Optional<T>` for null-safe lookups
- `Location` header on resource creation

---

## The 5 CRUD Endpoints

| Operation | Method | URL              | Success Code | Notes                    |
|-----------|--------|------------------|--------------|--------------------------|
| Read all  | GET    | `/api/v1/todos`  | 200 OK       |                          |
| Read one  | GET    | `/api/v1/todos/{id}` | 200 OK   | 404 if not found         |
| Create    | POST   | `/api/v1/todos`  | 201 Created  | + Location header        |
| Update    | PUT    | `/api/v1/todos/{id}` | 200 OK   | 404 if not found         |
| Delete    | DELETE | `/api/v1/todos/{id}` | 204 No Content | 404 if not found    |

---

## Key Concepts

### HTTP Method Semantics

| Method | Idempotent? | Safe? | Meaning |
|--------|-------------|-------|---------|
| GET    | ✅ Yes      | ✅ Yes | Read only, no side effects |
| POST   | ❌ No       | ❌ No  | Creates a new resource each time |
| PUT    | ✅ Yes      | ❌ No  | Replaces a resource completely |
| DELETE | ✅ Yes      | ❌ No  | Removes a resource |

**Idempotent** = calling N times = same result as calling once.
**Safe** = no state change. Only GET qualifies.

### HTTP Status Codes — Why Each One

- **200 OK** — request processed, here's the result
- **201 Created** — new resource was created (POST)
- **204 No Content** — done, nothing to return (DELETE)
- **404 Not Found** — resource doesn't exist

### @PathVariable vs @RequestBody vs @RequestParam
```
GET  /api/v1/todos/5              → @PathVariable Long id
GET  /api/v1/todos?completed=true → @RequestParam Boolean completed
POST /api/v1/todos  { json body } → @RequestBody Todo todo
```

### Optional<T> — The Right Way to Handle "Maybe Nothing"
```java
// ❌ Old way — null is a landmine
public Todo getTodoById(Long id) { return store.get(id); }  // null if missing!

// ✅ Modern way — explicit about absence
public Optional<Todo> getTodoById(Long id) {
    return Optional.ofNullable(store.get(id));  // forces caller to handle both cases
}
```

### Optional → ResponseEntity Pattern (clean, no if-else)
```java
return todoService.getTodoById(id)
        .map(ResponseEntity::ok)               // found → 200 OK with body
        .orElse(ResponseEntity.notFound().build()); // not found → 404
```

### Why HashMap instead of List for the data store?
- List lookup by ID = O(n) — scan every element
- HashMap lookup by key = O(1) — direct access
- Mirrors how real DB primary-key lookups work

### Why AtomicLong for ID generation?
- Thread-safe counter — `getAndIncrement()` is atomic (indivisible)
- Plain `long` would have a race condition: two threads could get the same ID
- In production the database handles ID generation (AUTO_INCREMENT / SEQUENCE)

### 201 Created + Location Header (HTTP Spec RFC 7231)
```java
URI location = ServletUriComponentsBuilder
        .fromCurrentRequest()   // current URL: /api/v1/todos
        .path("/{id}")          // append:       /4
        .buildAndExpand(created.getId())
        .toUri();               // result: http://localhost:8080/api/v1/todos/4

return ResponseEntity.created(location).body(created);
```
The `Location` header tells the client exactly where to find the new resource.

### PUT vs PATCH
- **PUT** = full replacement. All fields overwritten. What we implemented.
- **PATCH** = partial update. Only specified fields change.
- PUT is simpler and sufficient for most CRUD apps.

### Defensive copy pattern
```java
// ✅ Never return a direct reference to internal state
return new ArrayList<>(store.values());  // caller gets a copy

// ❌ Dangerous — caller can modify our internal store!
return store.values();
```

---

## Annotations Quick Reference

| Annotation | What it does |
|---|---|
| `@GetMapping("/{id}")` | Maps `GET /todos/{id}` to the method |
| `@PostMapping` | Maps `POST /todos` to the method |
| `@PutMapping("/{id}")` | Maps `PUT /todos/{id}` to the method |
| `@DeleteMapping("/{id}")` | Maps `DELETE /todos/{id}` to the method |
| `@PathVariable Long id` | Binds `{id}` from the URL path to the parameter |
| `@RequestBody Todo todo` | Deserializes JSON body into a `Todo` object |

---

## Files Changed
- `service/TodoService.java` — added `getTodoById`, `createTodo`, `updateTodo`, `deleteTodo`
- `service/impl/TodoServiceImpl.java` — full rewrite with HashMap + AtomicLong
- `controller/TodoController.java` — added 4 new endpoints

---

## Interview Questions & Answers

**Q: Why does POST return 201 and not 200?**
200 = "processed, here's data." 201 = "a new resource was created."
They communicate different semantics. HTTP spec says POST creating a resource should return 201.

**Q: Why does DELETE return 204 and not 200?**
After deletion there's nothing to return. 204 is honest: "done, no body."
200 with empty body is technically incorrect.

**Q: What is idempotency? Which HTTP methods are idempotent?**
Idempotent = multiple identical calls produce the same result as one call.
GET, PUT, DELETE are idempotent. POST is NOT (each call creates a new resource).

**Q: What is the difference between PUT and PATCH?**
PUT = full replacement of the resource. PATCH = partial update (only specified fields).

**Q: Why use Optional<T> instead of returning null?**
`null` is invisible — callers can forget to check it and get NPE at runtime.
`Optional<T>` makes absence explicit in the method signature — the compiler forces
you to handle both cases before you can access the value.

**Q: What is the Location header and when should you use it?**
HTTP spec says 201 Created responses should include a Location header pointing
to the URL of the newly created resource. Allows clients to fetch/bookmark it
without parsing the response body.

---

## Exercises
- [ ] Exercise 1: Run the app and test all 5 endpoints with Postman or curl
- [ ] Exercise 2: Try `GET /api/v1/todos/999` — you should get 404
- [ ] Exercise 3: POST a new todo, check the Location header, then GET that URL
- [ ] Exercise 4: DELETE a todo, then try to GET it — what do you get?
- [ ] Exercise 5: What happens if you POST with no body at all? What status code?

---

## Notes / Questions
_(Add your own notes here)_
