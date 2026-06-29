# Phase 9 — Pagination, Sorting & Filtering

## Goal
- `GET /api/v1/todos?page=0&size=10&sort=title,asc` — paginated + sorted
- `GET /api/v1/todos?completed=true` — filter by status
- `GET /api/v1/todos?search=spring` — keyword search in title/description
- `PagedResponse<T>` — consistent pagination envelope for clients
- Dynamic filter composition with JPA Specification

---

## API Examples

```
# Default (first page, 10 items, sorted by id ascending)
GET /api/v1/todos

# Page 2, 5 items per page
GET /api/v1/todos?page=1&size=5

# Sort by title descending
GET /api/v1/todos?sort=title,desc

# Only incomplete todos
GET /api/v1/todos?completed=false

# Search for "spring" in title or description
GET /api/v1/todos?search=spring

# Combine everything
GET /api/v1/todos?completed=false&search=spring&page=0&size=5&sort=title,asc
```

---

## PagedResponse — The Envelope

```json
{
  "content": [
    { "id": 1, "title": "Learn Spring Boot", "completed": false },
    { "id": 2, "title": "Learn JPA", "completed": false }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 42,
  "totalPages": 5,
  "last": false
}
```

- `content` — the items for this page
- `totalElements` — how many rows exist in total (used to build pagination UI)
- `totalPages` — how many pages at this size
- `last` — whether this is the last page (no "next" button needed)

---

## Key Spring Concepts

### Pageable — The Request Object
```java
// Spring MVC auto-binds these query params:
// ?page=0&size=10&sort=title,asc  →  Pageable object

@PageableDefault(size = 10, sort = "id", direction = Sort.Direction.ASC)
Pageable pageable
```
Client can override any default:
- `?page=2` → skip to page 3
- `?size=20` → 20 items per page
- `?sort=title,desc` → sort by title descending
- `?sort=completed,asc&sort=title,desc` → multi-field sort

### Page<T> — The DB Result
```java
// findAll(spec, pageable) returns Page<Todo>
page.getContent()       // List<Todo> for this page
page.getTotalElements() // total rows (COUNT(*) query runs automatically)
page.getTotalPages()    // Math.ceil(totalElements / size)
page.isLast()           // page.getNumber() == page.getTotalPages() - 1
```

Spring Data runs TWO queries automatically:
1. `SELECT * FROM todos WHERE ... LIMIT ? OFFSET ?`  → gets the content
2. `SELECT COUNT(*) FROM todos WHERE ...`             → gets the total

### @ModelAttribute — Binding Filter Params
```java
// Binds multiple query params into one object:
// ?completed=true&search=spring → TodoFilterRequest{completed=true, search="spring"}
@ModelAttribute TodoFilterRequest filter
```
Cleaner than declaring multiple `@RequestParam` fields on the method.

---

## JPA Specification — Dynamic Filtering

### Why Specification?
```
Without it, you need a query method per combination:
  findByCompleted()
  findByTitleContaining()
  findByCompletedAndTitleContaining()
  findByCompletedAndDescriptionContaining()
  findByCompletedAndTitleContainingOrDescriptionContaining() ...

With Specification: compose predicates at runtime — one method, infinite combinations.
```

### How It Works
```java
// Each Specification = one WHERE clause fragment
Specification<Todo> byCompleted = (root, query, cb) ->
        cb.equal(root.get("completed"), true);
//  generates: WHERE completed = true

Specification<Todo> bySearch = (root, query, cb) -> {
    String pattern = "%" + search.toLowerCase() + "%";
    return cb.or(
            cb.like(cb.lower(root.get("title")), pattern),
            cb.like(cb.lower(root.get("description")), pattern)
    );
};
// generates: WHERE (LOWER(title) LIKE '%spring%' OR LOWER(description) LIKE '%spring%')

// Compose them:
spec.and(bySearch);
// generates: WHERE completed = true AND (LOWER(title) LIKE '%spring%' OR ...)
```

### TodoSpecification.fromFilter() — The Key Method
```java
public static Specification<Todo> fromFilter(TodoFilterRequest filter) {
    Specification<Todo> spec = Specification.where(null); // start empty

    if (filter.getCompleted() != null) {
        spec = spec.and(hasCompleted(filter.getCompleted()));
    }

    if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
        spec = spec.and(hasSearchTerm(filter.getSearch()));
    }

    return spec;
}
```
- No filter provided → `Specification.where(null)` → no WHERE clause → returns all rows
- One filter → one predicate added
- Both filters → both predicates ANDed together

### Repository Must Extend JpaSpecificationExecutor
```java
public interface TodoRepository
        extends JpaRepository<Todo, Long>,
                JpaSpecificationExecutor<Todo> {
    // JpaSpecificationExecutor adds: findAll(Specification, Pageable)
}
```

---

## Files Created
- `dto/response/PagedResponse.java` — generic pagination envelope
- `dto/request/TodoFilterRequest.java` — filter params object
- `service/TodoSpecification.java` — Specification factory

## Files Changed
- `repository/TodoRepository.java` — added `JpaSpecificationExecutor`
- `service/TodoMapper.java` — added `toPagedResponse()`
- `service/TodoService.java` — replaced `getAllTodos()` with `getTodos(filter, pageable)`
- `service/impl/TodoServiceImpl.java` — implemented new method
- `controller/TodoController.java` — new endpoint with `@ModelAttribute` + `@PageableDefault`
- Tests updated to match new signatures

---

## Interview Questions & Answers

**Q: What is pagination and why is it necessary?**
Without pagination, a query on a large table returns all rows — slow DB scan,
high memory usage, unusable response. Pagination fetches a fixed-size slice:
`LIMIT ? OFFSET ?`. As data grows, performance stays constant per page.

**Q: What is the difference between Pageable and Page?**
`Pageable` = the REQUEST — what page, what size, what sort (input to the query).
`Page<T>` = the RESPONSE — the items + total count + metadata (output of the query).

**Q: How does Spring Data know to run a COUNT query automatically?**
When you call `findAll(Specification, Pageable)`, Spring Data runs two queries:
one for the content (`SELECT ... LIMIT ? OFFSET ?`) and one for the total
(`SELECT COUNT(*) FROM todos WHERE ...`). Both happen automatically.

**Q: What is JPA Specification?**
A type-safe way to build dynamic WHERE clauses using the JPA Criteria API.
A Specification is a single predicate (condition). Multiple Specifications can
be combined with `.and()`, `.or()`, `.not()` at runtime based on which filters
the client actually provides.

**Q: What is @ModelAttribute?**
Binds multiple HTTP query parameters into a single Java object.
`?completed=true&search=spring` → `TodoFilterRequest{completed=true, search="spring"}`.
Alternative to declaring multiple `@RequestParam` parameters.

**Q: What is @PageableDefault?**
Sets default pagination values used when the client doesn't specify them.
Without it, the defaults are page=0, size=20, unsorted.
`@PageableDefault(size=10, sort="id")` makes size=10 and sort=id the fallbacks.

---

## Exercises
- [ ] Exercise 1: Run the app, hit `GET /api/v1/todos?page=0&size=2` — verify only 2 items returned
- [ ] Exercise 2: Hit `GET /api/v1/todos?sort=title,desc` — verify alphabetical reverse order
- [ ] Exercise 3: Hit `GET /api/v1/todos?completed=true` — verify only completed todos returned
- [ ] Exercise 4: Hit `GET /api/v1/todos?search=spring` — verify only matching todos returned
- [ ] Exercise 5: Combine them: `GET /api/v1/todos?completed=false&search=learn&size=5`
- [ ] Exercise 6: Open H2 console while the app runs, and check the SQL printed in the terminal — see the LIMIT/OFFSET and COUNT queries Hibernate generates

---

## Notes / Questions
_(Add your own notes here)_
