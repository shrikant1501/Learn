package com.learnjava.todo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The entry point for the Spring Boot application.
 *
 * <p>
 * {@code @SpringBootApplication} is a convenience annotation that combines three annotations:
 * <ul>
 *   <li>{@code @Configuration} — marks this class as a source of Spring bean definitions</li>
 *   <li>{@code @EnableAutoConfiguration} — tells Spring Boot to start adding beans based on
 *       classpath settings, other beans, and various property settings. For example, if
 *       spring-webmvc is on the classpath, this annotation flags the application as a web
 *       application and activates key behaviors such as setting up a DispatcherServlet.</li>
 *   <li>{@code @ComponentScan} — tells Spring to scan this package (and sub-packages)
 *       for components, services, controllers, etc.</li>
 * </ul>
 *
 * <p>
 * <strong>Important placement rule:</strong> This class must be in the ROOT package
 * ({@code com.learnjava.todo}), so that {@code @ComponentScan} automatically picks up
 * all classes in sub-packages like {@code controller}, {@code service}, etc.
 * If you place it in a sub-package, Spring won't find your beans.
 */
@SpringBootApplication
public class TodoApplication {

    public static void main(String[] args) {
        /*
         * SpringApplication.run() does the following:
         *   1. Creates an ApplicationContext (Spring's IoC container)
         *   2. Registers all beans (@Component, @Service, @Controller, etc.)
         *   3. Starts the embedded Tomcat server
         *   4. Begins listening for HTTP requests
         *
         * The result: your entire application starts with one line.
         */
        SpringApplication.run(TodoApplication.class, args);
    }
}
