package com.learnjava.todo.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Request body for user login")
public class LoginRequest {

    @NotBlank(message = "Username is required")
    @Schema(description = "Registered username", example = "john_doe")
    private String username;

    @NotBlank(message = "Password is required")
    @Schema(description = "Account password", example = "secret123")
    private String password;
}
