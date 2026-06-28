# Phase 3 — Global Exception Handling

## Goal
- Centralized error handling via `@RestControllerAdvice`
- Consistent JSON error responses for ALL failure scenarios
- Custom `TodoNotFoundException` with meaningful context
- Controller contains ONLY happy-path code — zero error boilerplate

---

## The Problem This Phase Solves

**Before:** Each endpoint handled its own errors inconsistently.
**After:** One class catches everything, one shape for all errors.

```json
{
  "status": 404,
  "error": "NOT_FOUND",
  "message": "Todo with id 42 was not found",
  "path": "/api/v1/todos/42",
  "timestamp": "2026-06-29 12:00:00"
}
```

---

## Key Concepts

### @RestControllerAdvice
- = `@ControllerAdvice` + `@ResponseBody`
- Applies to ALL controllers in the application
- Methods annotated with `@ExceptionHandler` are called when exceptions are thrown
- Acts like a global try-catch wrapping every controller method invisibly

```
Request → Controller → throws Exception
                              ↓
                   @RestControllerAdvice catches it
                              ↓
                   Returns clean JSON error response
```

### How Spring Picks the Right Handler
Spring finds the MOST SPECIFIC `@ExceptionHandler` matching the thrown exception.
Just like Java's own multi-catch block ordering:
- `TodoNotFoundException` → matches `handleTodoNotFound` exactly
- `NullPointerException` → no specific handler → falls to `handleAllExceptions`

### Checked vs Unchecked Exceptions

| | Checked (`extends Exception`) | Unchecked (`extends RuntimeException`) |
|---|---|---|
| Compiler enforces | ✅ Must catch or declare `throws` | ❌ No requirement |
| Use when | Caller can meaningfully recover | Caller cannot recover / programming error |
| Spring convention | Rare | Default for business exceptions |

`TodoNotFoundException extends RuntimeException` — the right choice for Spring.
Checked exceptions would pollute every service interface method signature with `throws`.

### Custom Exception — Why Store the ID?
```java
public class TodoNotFoundException extends RuntimeException {
    private final Long id;  // ← stored for precise error messages

    public TodoNotFoundException(Long id) {
        super("Todo with id " + id + " was not found");
        this.id = id;
    }
}
```
Error message is built in the DOMAIN layer, not the HTTP layer.
This keeps HTTP concerns (status codes) separate from domain concerns (what's missing).

### orElseThrow() — The Controller Pattern

```java
// BEFORE Phase 3 — controller handles "not found" itself
return todoService.getTodoById(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());

// AFTER Phase 3 — controller only handles the happy path
Todo todo = todoService.getTodoById(id)
        .orElseThrow(() -> new TodoNotFoundException(id));
return ResponseEntity.ok(todo);
```
The controller no longer knows or cares about 404. It just asks for data.
If the data isn't there, the exception propagates → GlobalExceptionHandler → 404.

### ErrorResponse — The Consistent Error Contract
```java
@Getter
@Builder
public class ErrorResponse {
    private final int status;        // 404
    private final String error;      // "NOT_FOUND"
    private final String message;    // "Todo with id 42 was not found"
    private final String path;       // "/api/v1/todos/42"
    private final LocalDateTime timestamp;
}
```
Why `@Getter` + `@Builder` instead of `@Data`?
- Error responses are IMMUTABLE — no setters needed
- `@Data` would generate setters, allowing unintended mutation
- Always use the most restrictive annotations that satisfy requirements

### Security Rule — Never Leak Internals
```java
// ✅ Catch-all: log full details server-side, return generic message to client
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleAllExceptions(Exception ex, ...) {
    log.error("Unexpected error: {}", ex.getMessage(), ex);  // full stack trace in logs
    // "An unexpected error occurred. Please try again later."  ← to client
}
```
Never return stack traces, class names, or SQL errors in HTTP responses.
This is information disclosure (CWE-209) — a real security vulnerability.

---

## Files Created
- `exception/TodoNotFoundException.java` — custom unchecked exception
- `exception/ErrorResponse.java` — consistent JSON error shape
- `exception/GlobalExceptionHandler.java` — `@RestControllerAdvice` catching all errors

## Files Modified
- `controller/TodoController.java` — replaced `orElse(404)` with `orElseThrow()`

---

## Exception Handlers We Have

| Handler Method | Exception Caught | Status |
|---|---|---|
| `handleTodoNotFound` | `TodoNotFoundException` | 404 |
| `handleMalformedJson` | `HttpMessageNotReadableException` | 400 |
| `handleTypeMismatch` | `MethodArgumentTypeMismatchException` | 400 |
| `handleAllExceptions` | `Exception` (catch-all) | 500 |

---

## Interview Questions & Answers

**Q: What is @ControllerAdvice / @RestControllerAdvice?**
A cross-cutting concern class applied to all controllers. Methods annotated with
`@ExceptionHandler` intercept exceptions thrown from any controller before they
reach the client. `@RestControllerAdvice` = `@ControllerAdvice` + `@ResponseBody`.

**Q: What is the difference between checked and unchecked exceptions?**
Checked: compiler forces you to catch or declare. Unchecked: no enforcement.
In Spring, business exceptions like "not found" extend `RuntimeException` by convention
to avoid polluting service interface signatures with `throws` declarations.

**Q: Why should you never expose stack traces in HTTP responses?**
Information disclosure (CWE-209) — reveals internal class names, library versions,
and code structure. Makes it much easier for attackers to craft targeted exploits.
Always log full details server-side; return only generic messages to clients.

**Q: How does Spring choose which @ExceptionHandler method to call?**
It finds the most specific handler matching the thrown exception's class hierarchy,
exactly like Java's own multi-catch block. `TodoNotFoundException` matches before
the generic `Exception` catch-all.

**Q: Why is orElseThrow() better than orElse(ResponseEntity.notFound().build())?**
`orElseThrow()` keeps the controller on the happy path only — single responsibility.
It also produces a richer error (our `ErrorResponse` with message, path, timestamp)
vs a plain 404 with no body. And it works the same way across ALL controllers
without duplication.

---

## Exercises
- [ ] Exercise 1: Run the app and hit `GET /api/v1/todos/999` — see the structured 404
- [ ] Exercise 2: Hit `GET /api/v1/todos/abc` — see the 400 type-mismatch error
- [ ] Exercise 3: POST with body `{invalid}` — see the 400 malformed JSON error
- [ ] Exercise 4: Compare the response to what you got BEFORE Phase 3 — note the difference
- [ ] Exercise 5: Add a new custom exception `DuplicateTodoException` (don't implement it, just think about when you'd throw it)

---

## Notes / Questions
_(Add your own notes here)_
