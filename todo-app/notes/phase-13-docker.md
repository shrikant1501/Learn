# Phase 13 — Docker + PostgreSQL

## Goal
Replace H2 (in-memory) with PostgreSQL running in Docker.
The app runs locally connecting to a real database engine — same as production.

---

## New Files Created

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Declares postgres + app services, volumes, env vars |
| `Dockerfile` | Multi-stage build: Stage 1 compiles, Stage 2 runs |
| `.dockerignore` | Excludes target/, .git/, IDE files from Docker build context |
| `src/main/resources/application-docker.properties` | PostgreSQL config, active when `SPRING_PROFILES_ACTIVE=docker` |

## Modified Files

| File | Change |
|------|--------|
| `pom.xml` | Added `postgresql` JDBC driver (runtime scope) |

---

## How to Run With Docker (once Docker Desktop is installed)

```powershell
# 1. Start only PostgreSQL (for local dev without containerising the app)
cd todo-app
docker compose up postgres -d

# 2. Run the Spring Boot app locally pointing to Docker's PostgreSQL
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/tododb"
$env:SPRING_DATASOURCE_USERNAME="todo_user"
$env:SPRING_DATASOURCE_PASSWORD="todo_pass"
$env:SPRING_JPA_HIBERNATE_DDL_AUTO="update"
$env:SPRING_JPA_DATABASE_PLATFORM="org.hibernate.dialect.PostgreSQLDialect"
$env:SPRING_PROFILES_ACTIVE="docker"
mvn spring-boot:run

# OR — run the full stack (app + db) all in Docker:
docker compose up -d
# App at http://localhost:8080, Swagger at http://localhost:8080/swagger-ui.html

# 3. View logs
docker compose logs -f

# 4. Stop everything
docker compose down

# 5. Stop and delete the database volume (fresh start)
docker compose down -v
```

---

## Key Concepts

### Multi-Stage Dockerfile
```
Stage 1 (builder): eclipse-temurin:21-jdk-alpine
  → copies pom.xml → downloads deps (cached layer)
  → copies src/ → mvn package -DskipTests
  → produces: target/todo-app.jar

Stage 2 (runtime): eclipse-temurin:21-jre-alpine
  → copies ONLY the JAR from Stage 1
  → no JDK, no Maven, no source code in final image
  → runs as non-root user (security)
  → ~100MB image vs ~400MB if built naively
```

### Spring Profiles
```
application.properties              ← always loaded (base config, H2)
application-docker.properties       ← loaded when profile = docker (PostgreSQL)
Environment variables               ← highest priority, override everything
```
Priority: env vars > profile properties > base properties

### Docker Compose Service Communication
```
Host machine:
  localhost:5432 → postgres container (port mapping)
  localhost:8080 → app container (port mapping)

Inside Docker network:
  app container → postgres:5432 (service name DNS, no port needed)
  Uses: jdbc:postgresql://postgres:5432/tododb
```
The hostname `postgres` resolves to the container's IP automatically inside Docker's network.

### Named Volume
```yaml
volumes:
  postgres_data:/var/lib/postgresql/data
```
- `docker compose down` → stops containers, data persists in volume
- `docker compose down -v` → stops containers AND deletes volume (clean slate)
- Without volume → every `docker compose up` starts with an empty database

### JVM Flags for Containers
```
-XX:+UseContainerSupport   → JVM respects Docker CPU/memory limits
-XX:MaxRAMPercentage=75.0  → heap = 75% of container RAM
```
Without these, JVM reads host machine RAM (not container limit) and may OOM-kill.

### 12-Factor App — Config from Environment
Our `docker-compose.yml` passes database credentials as env vars:
```yaml
environment:
  SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/tododb
```
Spring Boot automatically converts `SPRING_DATASOURCE_URL` → `spring.datasource.url`.
This is the **12-factor app** pattern: config is separate from code, injected at runtime.

---

## Common Interview Questions

**Q: What is a Docker multi-stage build and why use it?**
Stage 1 (builder) has JDK + Maven → compiles the app.
Stage 2 (runtime) has JRE only → copies the JAR from Stage 1.
Result: final image contains no build tools, no source code — smaller and more secure.

**Q: Why keep H2 for tests even after adding PostgreSQL?**
H2 runs in-memory, starts in milliseconds, needs no external process.
Using PostgreSQL in unit tests would require Docker running during CI — slow and brittle.
Integration tests (Phase 15, Testcontainers) use a real PostgreSQL in a test container.

**Q: What is `ddl-auto=update` and is it safe for production?**
`update` → Hibernate alters the schema to match entities (adds columns, creates tables).
NOT safe for production: it never drops columns (data loss risk), and schema changes
should be reviewed and versioned. Production uses `validate` + Flyway (Phase 14).

**Q: What is the difference between Docker image and container?**
Image = read-only template (like a class in OOP).
Container = running instance of an image (like an object).
Many containers can run from one image simultaneously.

**Q: What does `depends_on: condition: service_healthy` do?**
Tells Docker Compose: "don't start the app container until the postgres container
passes its health check (`pg_isready`)". Without this, the app might start before
PostgreSQL is ready to accept connections → connection refused error on startup.

---

## Test Results
```
All 30 tests still pass with H2 (unit tests unaffected by PostgreSQL addition)
Tests run: 30, Failures: 0, Errors: 0 — BUILD SUCCESS
```

---

## Next Step — Docker Installation Required

Docker Desktop must be installed before running `docker compose up`.
Download from: https://www.docker.com/products/docker-desktop/

After installation, verify:
```powershell
docker --version        # Docker version 27.x.x
docker compose version  # Docker Compose version v2.x.x
```
