package com.learnjava.todo.integration;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// ============================================================================
// AbstractIntegrationTest — shared base class for all integration tests
//
// WHY A BASE CLASS?
//   Every integration test needs:
//     1. A running PostgreSQL container
//     2. Spring context started against that container
//     3. MockMvc wired up for HTTP requests
//   Putting this in a base class means we write it ONCE and every subclass
//   inherits the full setup. Adding a new integration test = just extend + write tests.
//
// STATIC CONTAINER — the key performance decision:
//   @Container on a STATIC field means the container starts ONCE for the entire
//   test suite (not once per test class). Spring Boot reuses the same ApplicationContext
//   (context caching) across test classes that share the same configuration.
//   Result: PostgreSQL starts once → all integration tests run → PostgreSQL stops.
//   Without 'static': a new container + new Spring context per test class = very slow.
// ============================================================================

// @Testcontainers: activates the Testcontainers JUnit 5 extension.
// It scans for fields annotated @Container and manages their lifecycle automatically.
// Without this, @Container fields are ignored — the container never starts.
@Testcontainers

// @SpringBootTest: loads the full ApplicationContext — every @Bean, @Service,
// @Repository, @Controller is created. This is the opposite of @WebMvcTest.
// webEnvironment = RANDOM_PORT would start a real server; MOCK is faster and
// sufficient because @AutoConfigureMockMvc gives us a MockMvc that hits the full stack.
@SpringBootTest

// @AutoConfigureMockMvc: wires MockMvc to the full Spring context.
// We get HTTP request simulation WITH the full filter chain (security, etc.)
// WITHOUT a real HTTP server. Best of both worlds.
@AutoConfigureMockMvc

// @ActiveProfiles("test"): tells Spring to load application-test.properties
// on top of the base application.properties.
// This activates PostgreSQL dialect, disables H2 console, sets ddl-auto=none.
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    // -------------------------------------------------------------------------
    // PostgreSQL container — STATIC so it is shared across ALL test classes
    // -------------------------------------------------------------------------

    // PostgreSQLContainer is Testcontainers' pre-built wrapper for PostgreSQL.
    // It knows the right Docker image, the default port (5432), and how to check
    // readiness (it waits until PostgreSQL is actually accepting connections).
    //
    // "postgres:16-alpine" — same version as our docker-compose.yml.
    // Using the same version in tests as in production closes the "works on my machine"
    // gap: if a SQL feature doesn't exist in Postgres 16, the test will catch it.
    //
    // withDatabaseName/Username/Password: these set the test database credentials.
    // They don't need to match production — Testcontainers passes them to the
    // container AND generates the correct JDBC URL for us.
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tododb_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    // -------------------------------------------------------------------------
    // @DynamicPropertySource — the bridge between Testcontainers and Spring
    // -------------------------------------------------------------------------

    // The container starts on a RANDOM port (e.g., 55432) to avoid conflicts with
    // any PostgreSQL already running on your machine. We can't know this port
    // at compile time or in properties files — it's only known after the container starts.
    //
    // @DynamicPropertySource solves this: it is called AFTER the container starts
    // but BEFORE the Spring ApplicationContext is initialised. We override the three
    // datasource properties with the container's actual runtime values.
    //
    // POSTGRES.getJdbcUrl()   → "jdbc:postgresql://localhost:55432/tododb_test"
    // POSTGRES.getUsername()  → "test_user"
    // POSTGRES.getPassword()  → "test_pass"
    //
    // Spring picks these up and uses them for all JPA / Flyway connections.
    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Tell Spring which JDBC driver to use — PostgreSQL, not H2
        registry.add("spring.datasource.driver-class-name",
                () -> "org.postgresql.Driver");
    }
}
