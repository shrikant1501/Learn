package com.learnjava.todo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    // The name we use to reference this scheme in @SecurityRequirement annotations
    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI todoApiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Todo API")
                        .description("""
                                A RESTful API for managing todo items.
                                
                                Built with Spring Boot as a learning project — \
                                demonstrating clean architecture, JPA, validation, \
                                exception handling, pagination, and JWT authentication.
                                
                                **Authentication:** Use POST /api/v1/auth/register or /login \
                                to get a token, then click the Authorize button above and enter: \
                                `Bearer <your-token>`
                                
                                **Base URL:** `/api/v1`
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Learning Project")
                                .email("learn@example.com"))
                        .license(new License()
                                .name("MIT License")))

                // Declares the global security requirement — every endpoint shows a lock icon
                // unless explicitly marked with @SecurityRequirement(name = "") to opt out
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))

                // Defines the security scheme — tells Swagger UI to show an Authorize button
                // where users paste their JWT. The "bearer" format and "JWT" bearerFormat
                // are informational metadata for documentation tools.
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)   // HTTP authentication
                                        .scheme("bearer")                  // Bearer token scheme
                                        .bearerFormat("JWT")));            // informational only
    }
}
