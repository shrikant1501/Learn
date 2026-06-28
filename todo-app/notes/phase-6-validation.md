# Phase 6 — Bean Validation

## Goal
- Validation rules on request DTOs — declared as annotations
- API rejects bad input BEFORE it reaches the service
- Field-level error responses with precise messages per violated field
- Zero validation code in controller or service — fully declarative

---

## The Three Parts That Work Together

```
@Valid on controller parameter    ← tells Spring: "validate this object"
      ↓
@NotBlank / @Size on DTO fields   ← the actual rules
      ↓
MethodArgumentNotValidException   ← thrown if any rule fails
      ↓
GlobalExceptionHandler catches it ← returns clean 400 with field errors
```

The controller method body NEVER executes on a validation failure.

---

## Validation Annotations (Jakarta Validation / JSR-380)

| Annotation | What it checks |
|---|---|
| `@NotNull` | value is not null |
| `@NotEmpty` | not null AND not empty string/collection |
| `@NotBlank` | not null AND not empty AND not just whitespace |
| `@Size(min, max)` | string/collection length within range |
| `@Min(n)` / `@Max(n)` | numeric value range |
| `@Email` | valid email format |
| `@Pattern(regexp)` | matches a regex |
| `@Positive` | number > 0 |
| `@Future` / `@Past` | date is in the future / past |

### @NotBlank vs @NotEmpty vs @NotNull

```java
String value = "   ";  // three spaces

@NotNull   → PASSES  (it's not null)
@NotEmpty  → PASSES  (it's not empty — it has spaces)
@NotBlank  → FAILS   (it IS blank — only whitespace)
```
Always use `@NotBlank` for required text fields like title.

---

## CreateTodoRequest — Validation Rules

```java
@NotBlank(message = "Title must not be blank")
@Size(min = 1, max = 255, message = "Title must be between 1 and 255 characters")
private String title;

@Size(max = 1000, message = "Description must not exceed 1000 characters")
private String description;   // optional — no @NotBlank

private Boolean completed;    // optional — no constraint
```

---

## The Validation Error Response

```json
POST /api/v1/todos   body: {"title": ""}

{
  "status": 400,
  "error": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/v1/todos",
  "timestamp": "2026-06-29 12:00:00",
  "errors": {
    "title": "Title must not be blank"
  }
}
```

For a non-validation 404, the `errors` field is absent entirely:
```json
{
  "status": 404,
  "error": "NOT_FOUND",
  "message": "Todo with id 999 was not found",
  "path": "/api/v1/todos/999",
  "timestamp": "2026-06-29 12:00:00"
}
```
`@JsonInclude(NON_NULL)` on `ErrorResponse` makes null fields disappear from JSON.

---

## How the Handler Extracts Field Errors

```java
Map<String, String> fieldErrors = ex.getBindingResult()
        .getFieldErrors()
        .stream()
        .collect(Collectors.toMap(
                FieldError::getField,           // "title"
                FieldError::getDefaultMessage,  // "Title must not be blank"
                (existing, replacement) -> existing  // keep first if multiple failures on same field
        ));
```

`ex.getBindingResult()` — the validation result object
`getFieldErrors()` — one `FieldError` per violated constraint
`FieldError::getField` — method reference for the field name
`FieldError::getDefaultMessage` — the `message` value from the annotation

---

## @JsonInclude(NON_NULL) — Why It Matters

Without it:
```json
{ "status": 404, "error": "NOT_FOUND", ..., "errors": null }
```

With `@JsonInclude(NON_NULL)`:
```json
{ "status": 404, "error": "NOT_FOUND", ... }
```

The `errors` field only appears when it has a value. Clean, minimal responses.

---

## Files Changed
- `pom.xml` — added `spring-boot-starter-validation`
- `dto/request/CreateTodoRequest.java` — added `@NotBlank`, `@Size`
- `dto/request/UpdateTodoRequest.java` — added `@NotBlank`, `@Size`
- `controller/TodoController.java` — added `@Valid` to POST and PUT parameters
- `exception/ErrorResponse.java` — added `errors` field + `@JsonInclude(NON_NULL)`
- `exception/GlobalExceptionHandler.java` — added `handleValidation` method

## Files NOT Changed
- `TodoService` interface, `TodoServiceImpl`, `TodoRepository` — untouched
- `TodoResponse`, `Todo` entity — untouched

---

## Interview Questions & Answers

**Q: What is Bean Validation / JSR-380?**
A Java standard (not Spring-specific) for declaring constraints as annotations on fields.
`@NotBlank`, `@Size`, `@Email`, etc. are from `jakarta.validation.constraints`.
Hibernate Validator is the reference implementation. Spring integrates it transparently.

**Q: What is the difference between @NotNull, @NotEmpty, and @NotBlank?**
`@NotNull`: value != null
`@NotEmpty`: value != null AND length > 0 (still passes for "   ")
`@NotBlank`: value != null AND trimmed length > 0 (fails for "   ")
Use `@NotBlank` for required text fields.

**Q: What happens when validation fails? What exception is thrown?**
Spring throws `MethodArgumentNotValidException` before the controller method body runs.
The exception contains a `BindingResult` with one `FieldError` per violated constraint.
`GlobalExceptionHandler.handleValidation()` catches it and returns a 400 response.

**Q: What is the difference between @Valid and @Validated?**
`@Valid` (Jakarta standard): triggers basic Bean Validation constraints.
`@Validated` (Spring): also supports validation groups — you can define different
constraint sets for different operations (create vs update vs admin update).
`@Valid` is sufficient for most use cases.

**Q: Why validate on the DTO instead of the entity?**
Entity constraints (@Column(nullable=false)) fire at SQL execution time — deep
inside persistence layer, too late, produces database-level exceptions.
DTO constraints fire at the HTTP boundary — before any service or DB code runs.
Fail fast, fail clearly, fail at the boundary.

**Q: What is @JsonInclude(NON_NULL)?**
Jackson annotation that omits null fields from the serialized JSON output.
Without it: `"errors": null` always appears in every response.
With it: `errors` only appears when it has a value (validation failures only).

---

## Exercises
- [ ] Exercise 1: POST `{"title": ""}` — see the VALIDATION_FAILED 400 with field errors
- [ ] Exercise 2: POST `{"title": "  "}` (spaces) — see @NotBlank catching whitespace-only
- [ ] Exercise 3: POST `{}` (empty body) — see what happens (both title constraints fire)
- [ ] Exercise 4: POST without Content-Type header — which handler fires, validation or malformed JSON?
- [ ] Exercise 5: Add `@NotNull(message = "Completed field is required")` to `completed` in CreateTodoRequest — what changes?

---

## Notes / Questions
_(Add your own notes here)_
