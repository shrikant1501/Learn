package com.learnjava.todo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// @Configuration marks this class as a source of Spring bean definitions.
// Spring will call @Bean methods and register their return values in the ApplicationContext.
@Configuration
public class OpenApiConfig {

    // This @Bean produces the OpenAPI object that SpringDoc uses to populate
    // the top section of the Swagger UI (title, description, version, contact).
    @Bean
    public OpenAPI todoApiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Todo API")
                        .description("""
                                A RESTful API for managing todo items.
                                
                                Built with Spring Boot as a learning project — \
                                demonstrating clean architecture, JPA, validation, \
                                exception handling, and API documentation.
                                
                                **Base URL:** `/api/v1`
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Learning Project")
                                .email("learn@example.com"))
                        .license(new License()
                                .name("MIT License")));
    }
}
