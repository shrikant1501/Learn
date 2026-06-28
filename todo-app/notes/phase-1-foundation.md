# Phase 1 — Project Foundation & First REST Endpoint

## Goal
- Properly structured Spring Boot project
- Clean, production-oriented package structure
- First working REST endpoint: `GET /api/v1/todos`
- Understanding of WHY everything is set up this way

---

## Key Concepts

### Three-Layer Architecture
```
CLIENT
  ↓ HTTP
CONTROLLER   ← Handles HTTP, talks to client (zero business logic)
  ↓ Method calls
SERVICE      ← Business logic lives here (the brain)
  ↓ Method calls
REPOSITORY   ← Data access / database
```
**Why?** Single Responsibility, swappable layers, easy to test each layer in isolation.

### Interface + Implementation Pattern
- `TodoService` = interface (the contract)
- `TodoServiceImpl` = the actual implementation

**Why?**
- Dependency Inversion Principle (SOLID - D)
- Controller depends on the interface, not the concrete class
- You can inject a mock in tests without changing the controller
- Multiple implementations possible (e.g. InMemory now, Database later)

### Constructor Injection (preferred over @Autowired field injection)
```java
// ✅ Preferred
@RequiredArgsConstructor
public class TodoController {
    private final TodoService todoService;  // final = cannot be null
}

// ❌ Avoid
@Autowired
private TodoService todoService;
```
**Why constructor injection?**
1. Dependencies are explicit and visible
2. Class can be instantiated in tests without Spring
3. `final` guarantees no null
4. Recommended by the Spring team

### API Versioning
Always prefix endpoints with `/api/v1/` from day one.
Allows old clients to keep using v1 while new clients use v2.

### ResponseEntity vs returning object directly
```java
// ✅ Preferred — full control over HTTP response
return ResponseEntity.ok(todos);           // 200 OK
return ResponseEntity.created(uri).build(); // 201 Created
return ResponseEntity.notFound().build();   // 404 Not Found

// ❌ Less control — always returns 200
return todos;
```

---

## Annotations Quick Reference

| Annotation | What it does |
|---|---|
| `@SpringBootApplication` | `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan` |
| `@RestController` | `@Controller` + `@ResponseBody` — returns JSON data |
| `@RequestMapping("/api/v1/todos")` | URL prefix for all methods in the class |
| `@GetMapping` | Maps HTTP GET to the method |
| `@Service` | Marks class as a Spring bean containing business logic |
| `@RequiredArgsConstructor` | Lombok: generates constructor for all `final` fields |
| `@Slf4j` | Lombok: generates `private static final Logger log = ...` |
| `@Data` | Lombok: generates getters, setters, equals, hashCode, toString |
| `@Builder` | Lombok: implements the Builder design pattern |

---

## Package Structure
```
com.learnjava.todo
├── controller       ← REST controllers (HTTP layer)
├── service          ← Business logic interfaces
│   └── impl         ← Business logic implementations
├── repository       ← Spring Data JPA (future)
├── model            ← Domain objects / JPA entities (future)
├── dto              ← Data Transfer Objects (future)
├── exception        ← Custom exceptions (future)
├── config           ← Spring configuration (future)
└── TodoApplication  ← Main entry point (@SpringBootApplication must be in root)
```

---

## Files Created
- `pom.xml` — Maven build descriptor
- `src/main/resources/application.properties` — App configuration
- `TodoApplication.java` — Main entry point
- `model/Todo.java` — Todo domain object
- `service/TodoService.java` — Service interface
- `service/impl/TodoServiceImpl.java` — In-memory implementation
- `controller/TodoController.java` — REST controller
- `test/.../TodoApplicationTest.java` — Smoke test (context loads)

---

## Interview Questions & Answers

**Q: Difference between @Component, @Service, @Repository, @Controller?**
All are specializations of `@Component`, all detected by component scan.
Difference is semantic + functional: `@Repository` adds DB exception translation,
`@Controller` signals HTTP handling. Always use the most specific annotation.

**Q: Why constructor injection over field injection?**
Explicit dependencies, no Spring needed for instantiation in tests,
`final` guarantees no null, officially recommended by Spring team.

**Q: What does @SpringBootApplication do?**
Composite of `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`.
`@EnableAutoConfiguration` reads the classpath and wires beans automatically.

**Q: What is a fat JAR?**
Self-contained JAR with all dependencies (including Tomcat) bundled inside.
Deploy one file, run with `java -jar app.jar`. No separate server install needed.

**Q: What is ResponseEntity?**
Wrapper giving full control over HTTP status code, headers, and body.
Use it for all endpoints so you can return 201, 404, 204, etc. precisely.

---

## Exercises Completed
- [x] Exercise 1: Run the app, hit `GET /api/v1/todos` in browser/Postman
- [x] Exercise 2: Added 4th todo using Builder pattern
- [ ] Exercise 3: Think about adding a `completedAt` timestamp field
- [ ] Exercise 4: Explore `ResponseEntity` methods (202 Accepted, etc.)

---

## Notes / Questions
_(Add your own notes here as you learn)_

