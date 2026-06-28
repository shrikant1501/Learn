package com.learnjava.todo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test — verifies that the Spring ApplicationContext loads without errors.
 *
 * <p>
 * This is the most basic test in a Spring Boot application. It does not test
 * any business logic. Its only purpose is to verify that:
 * <ul>
 *   <li>All beans are properly defined and can be created</li>
 *   <li>All dependency injections are satisfied</li>
 *   <li>The application can start up without misconfiguration</li>
 * </ul>
 *
 * <p>
 * If this test fails, your application won't start in production either.
 * It's your first line of defense against configuration errors.
 */
@SpringBootTest
class TodoApplicationTest {

    @Test
    void contextLoads() {
        /*
         * An empty test body is intentional here.
         * The test framework will call @SpringBootTest's setup,
         * which starts the full ApplicationContext.
         * If the context fails to load, this test will fail with an exception.
         * No assertions are needed — the act of loading IS the assertion.
         */
    }
}
