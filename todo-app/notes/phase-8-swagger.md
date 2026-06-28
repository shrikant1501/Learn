# Phase 8 — API Documentation (Swagger / OpenAPI)

## Goal
- Auto-generated interactive docs at `http://localhost:8080/swagger-ui.html`
- Machine-readable OpenAPI spec at `http://localhost:8080/v3/api-docs`
- Every endpoint with descriptions, parameter docs, response codes, and examples
- Zero manual YAML writing — everything generated from code

---

## Key Concepts

### OpenAPI vs Swagger
- **OpenAPI** = the specification standard (describes your API in JSON/YAML)
- **Swagger** = the original tool that became the OpenAPI standard + the UI tooling
- **Swagger UI** = the interactive browser interface that renders an OpenAPI spec
- **SpringDoc** = the library that generates OpenAPI spec from your Spring code

### Springfox vs SpringDoc
| | Springfox | SpringDoc |
|---|---|---|
| Spring Boot 3 compatible | ❌ No | ✅ Yes |
| Actively maintained | ❌ No | ✅ Yes |
| Use | Legacy projects | All new projects |

Always use SpringDoc for Spring Boot 3.x.

---

## Annotations Quick Reference

| Annotation | Where | What it shows in UI |
|---|---|---|
| `@Tag(name, description)` | Controller class | Groups endpoints under a named section |
| `@Operation(summary, description)` | Controller method | Endpoint title and longer description |
| `@ApiResponse(responseCode, description)` | Controller method | Documents a specific HTTP status code |
| `@ApiResponses({...})` | Controller method | Multiple status codes at once |
| `@Parameter(description, example)` | Method parameter | Documents a path/query parameter |
| `@Schema(description, example)` | DTO class/field | Field description and example value |

---

## File Structure

```
config/
└── OpenApiConfig.java    ← global API info (title, version, description, contact)

controller/
└── TodoController.java   ← @Tag, @Operation, @ApiResponses, @Parameter

dto/
├── request/
│   ├── CreateTodoRequest.java  ← @Schema on class and fields
│   └── UpdateTodoRequest.java  ← @Schema on class and fields
└── response/
    └── TodoResponse.java       ← @Schema on class and fields
```

---

## Two URLs to Know

| URL | What you see |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Interactive Swagger UI |
| `http://localhost:8080/v3/api-docs` | Raw OpenAPI JSON spec |

The JSON spec can be imported into Postman, API gateway tools, or code generators.

---

## What @Tag Does

Without `@Tag`: SpringDoc uses the class name `TodoController` as the section title.
With `@Tag(name = "Todos", description = "CRUD operations for managing todo items")`:
- All endpoints are grouped under "Todos" in the UI
- The description appears under the section header

---

## How SpringDoc Auto-Generates Docs

SpringDoc reads at startup:
```
@RestController          → discovers this is a controller
@RequestMapping          → the base path
@GetMapping("/{id}")     → GET endpoint with path variable
@PathVariable Long id    → parameter type inferred as Long
ResponseEntity<TodoResponse> → response schema inferred from TodoResponse
@Valid @RequestBody      → request body schema inferred from request DTO
```

Without any annotations, you already get:
- All endpoints listed
- Path and query parameters typed correctly
- Request body schema from your DTO fields
- Response schema from your response DTO fields
- Try-it-out execution capability

The `@Operation`, `@ApiResponse`, `@Schema` annotations only **enrich** the auto-generated output.

---

## OpenApiConfig — What It Produces

```java
@Bean
public OpenAPI todoApiOpenAPI() {
    return new OpenAPI()
            .info(new Info()
                    .title("Todo API")
                    .version("1.0.0")
                    .description("...")
                    .contact(new Contact().name("Learning Project"))
                    .license(new License().name("MIT")));
}
```

This populates the header section of Swagger UI — the title, version badge,
description paragraph, and contact/license links at the top of the page.

---

## application.properties Settings

```properties
springdoc.swagger-ui.path=/swagger-ui.html   # URL for the UI
springdoc.api-docs.path=/v3/api-docs          # URL for the raw JSON spec
springdoc.swagger-ui.operationsSorter=method  # sort endpoints by HTTP method
springdoc.swagger-ui.tryItOutEnabled=false    # user must click "Try it out" to enable
```

---

## Interview Questions & Answers

**Q: What is OpenAPI and why does it matter?**
A standard specification for describing REST APIs in machine-readable format.
It enables: auto-generated documentation, client code generation, API mocking,
contract testing, and API gateway integration — all from a single spec file.

**Q: What is the difference between Swagger and OpenAPI?**
Originally "Swagger" was both the spec and the tooling (Swagger UI, Swagger Editor).
In 2016, the spec was donated to the OpenAPI Initiative and renamed "OpenAPI".
"Swagger" now refers to the tooling (Swagger UI, Swagger Editor) built around the spec.

**Q: Why use SpringDoc instead of Springfox?**
Springfox is unmaintained and incompatible with Spring Boot 3 / Spring 6.
SpringDoc is the actively maintained, Spring Boot 3-compatible replacement.

**Q: What does @Operation do?**
Documents a controller method in the OpenAPI spec.
`summary` = short title shown in the endpoint list.
`description` = longer text shown when the endpoint is expanded.

**Q: Where does the request body schema in Swagger UI come from?**
SpringDoc reads the `@RequestBody` parameter type (e.g. `CreateTodoRequest`)
and reflects its fields to build the schema. `@Schema` annotations on those fields
add descriptions and example values — but even without `@Schema`, the schema is generated.

---

## Exercises
- [ ] Exercise 1: Start the app and open http://localhost:8080/swagger-ui.html
- [ ] Exercise 2: Expand the "Todos" section, click "Get all todos", click "Try it out", click "Execute" — you should see the live response
- [ ] Exercise 3: Try a POST through Swagger UI — the example values from @Schema pre-fill the request body
- [ ] Exercise 4: Open http://localhost:8080/v3/api-docs — read the raw JSON. Find where your @Operation summary appears in it
- [ ] Exercise 5: Add `@Tag(name = "Health", description = "App health endpoints")` to a new controller with just one endpoint — see it appear as a separate section in Swagger UI

---

## Notes / Questions
_(Add your own notes here)_
