# Phase 18 — Actuator + Health Checks + Metrics

## Goal
Add production-readiness observability to the Todo API using Spring Boot Actuator:
- Expose `/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/loggers`
- Secure actuator endpoints (public health/info, ADMIN-only for the rest)
- Write a custom `HealthIndicator` that validates the todo repository is reachable
- Record a Micrometer `Counter` for every todo creation, visible at `/actuator/metrics/todos.created`

---

## Concepts Learned

### Spring Boot Actuator
- Ships with `spring-boot-starter-actuator` — just add the dependency, endpoints appear automatically
- Default: only `/health` and `/info` are exposed; configure `management.endpoints.web.exposure.include`
- `management.endpoint.health.show-details=always` — show component breakdown (db, diskSpace, todo, etc.)
- `management.endpoint.health.show-components=always` — list every registered health component

### HealthIndicator
- Implement `org.springframework.boot.actuate.health.HealthIndicator` interface
- Override `health()` — return `Health.up()` or `Health.down()` with optional details
- Spring auto-discovers every `@Component` implementing `HealthIndicator`
- Key derived by stripping `HealthIndicator` suffix + lowercasing: `TodoHealthIndicator` → `"todo"`
- Kubernetes probes call `/actuator/health` — `DOWN` triggers pod restart

### Micrometer (metrics facade)
- Vendor-neutral: same API targets Prometheus, Datadog, CloudWatch, or in-memory (default)
- Core types: `Counter` (cumulative count), `Gauge` (current value), `Timer` (duration + count)
- `Counter.builder("todos.created").description("...").register(meterRegistry)` — registers and returns the counter
- Increment AFTER successful operation: `todosCreatedCounter.increment()` — failed saves don't count
- Prometheus naming: dots become underscores + `_total` suffix → `todos_created_total`
- Visible at `GET /actuator/metrics/todos.created`

### SecurityConfig changes
```java
.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
.requestMatchers("/actuator/**").hasRole("ADMIN")
```
- Rule ORDER matters — first match wins
- Health/info are public (load balancers, K8s probes need no credentials)
- Everything else (`/metrics`, `/loggers`, `/env`) requires ADMIN

---

## Files Changed

### New Files
- `src/main/java/com/learnjava/todo/actuator/TodoHealthIndicator.java`
- `src/test/java/com/learnjava/todo/actuator/TodoHealthIndicatorTest.java`

### Modified Files
- `pom.xml` — added `spring-boot-starter-actuator`
- `src/main/resources/application.properties` — actuator config + info.app.* properties
- `src/main/java/com/learnjava/todo/config/SecurityConfig.java` — actuator security rules
- `src/main/java/com/learnjava/todo/service/impl/TodoServiceImpl.java` — MeterRegistry + Counter
- `src/test/java/com/learnjava/todo/service/TodoServiceImplTest.java` — SimpleMeterRegistry in setUp

---

## Design Decisions

### Why explicit constructor instead of @RequiredArgsConstructor?
`Counter` cannot be injected by Spring — it must be *built* from `MeterRegistry` using the builder API.
`@RequiredArgsConstructor` only handles direct injection of Spring beans.
An explicit constructor lets us inject `MeterRegistry` (a bean) and build `Counter` from it at construction time.

### Why SimpleMeterRegistry in unit tests?
`Counter.builder().register()` calls real registry methods — mocking `MeterRegistry` with Mockito
would require stubbing many internal calls. `SimpleMeterRegistry` is a real, lightweight, no-I/O
implementation that works without Spring. It's the idiomatic test choice for Micrometer unit tests.

### Why increment AFTER save()?
If `save()` throws, the transaction rolls back — the todo was not actually created.
Incrementing after success ensures the counter reflects committed work, not attempted work.

---

## Test Results
```
TodoHealthIndicatorTest  →  3 tests  ✅
TodoControllerTest       → 14 tests  ✅
TodoCacheTest            →  5 tests  ✅
TodoMapperTest           →  6 tests  ✅
TodoServiceImplTest      → 10 tests  ✅
TodoApplicationTest      →  1 test   ✅
AuthIntegrationTest      →  5 tests  SKIPPED (Docker unavailable)
TodoIntegrationTest      → 10 tests  SKIPPED (Docker unavailable)

Total: 54 run, 0 failures, 0 errors, 15 skipped → BUILD SUCCESS
```

---

## Interview Questions

1. **What does Spring Boot Actuator give you out of the box?**
   Health, info, metrics, loggers, env, beans, mappings, etc. — all accessible via HTTP (and JMX).
   Only health + info are exposed by default; you must opt in to the rest.

2. **How does Spring discover your custom HealthIndicator?**
   Component scan finds every `@Component` implementing `HealthIndicator` and registers it
   with `CompositeHealthContributor`. The name is derived from the class name automatically.

3. **What happens to `/actuator/health` if your custom indicator returns DOWN?**
   The overall status becomes `DOWN` (HTTP 503). Kubernetes readiness probe fails → pod
   is removed from the load balancer. Liveness probe fails → pod is restarted.

4. **What is Micrometer and why do we use it instead of a vendor library?**
   Micrometer is a metrics facade (like SLF4J for logging). You write `Counter.increment()` once
   and choose the backend (Prometheus, Datadog, etc.) at deployment time via configuration.

5. **Counter vs Gauge vs Timer — when to use each?**
   - Counter: cumulative count of events (todos created, errors, logins) — monotonically increasing
   - Gauge: current value that goes up and down (queue depth, active connections, cache size)
   - Timer: records both duration and invocation count (API response time, DB query time)

6. **Why not use @RequiredArgsConstructor when you need to build a Micrometer Counter?**
   Because Counter is not a Spring bean — it's built via `Counter.builder().register(registry)`.
   You need an explicit constructor to inject MeterRegistry and then construct Counter from it.

7. **Why use SimpleMeterRegistry in unit tests?**
   It's a real, in-memory implementation of MeterRegistry — no Spring context, no I/O, zero config.
   Mocking MeterRegistry is complex (too many internal interactions). SimpleMeterRegistry is the
   recommended approach in Micrometer's own test suite.

---

## Best Practices

- Never expose all actuator endpoints without authentication in production
- Use `show-details=when_authorized` in production (not `always`)
- Name Micrometer metrics with dots: `todos.created` — Prometheus adapter auto-converts to underscores
- Add `.description()` to every Counter/Timer — it appears in `/actuator/metrics/<name>` and docs
- Add `.tags("layer", "service")` to counters for cardinality-based filtering in dashboards
- Increment counters only on success — failed operations skew monitoring if counted

---

## Possible Improvements

- Add a `Timer` around `createTodo()` to measure P99 latency: `Timer.record(() -> save())`
- Add tags to the counter: `.tag("result", "success")` vs `.tag("result", "failure")`
- Add `@ConditionalOnProperty` to `TodoHealthIndicator` so it can be disabled per-profile
- Integrate Prometheus: add `micrometer-registry-prometheus`, expose `/actuator/prometheus`
- Add Grafana dashboard definition (JSON) to scrape the Prometheus endpoint

---

## Exercises

1. Add a `Gauge` to `TodoHealthIndicator` that tracks current todo count without a separate DB call.
2. Add a `Timer` around `getTodos()` to record how long paginated queries take.
3. Hit `/actuator/metrics/todos.created` in Swagger, create 3 todos, hit it again — observe the count change.
4. Change `show-details` to `when_authorized` and verify unauthenticated health calls hide the details.
5. Add a custom tag to the counter: `.tag("source", "api")` — observe it in `/actuator/metrics/todos.created`.
