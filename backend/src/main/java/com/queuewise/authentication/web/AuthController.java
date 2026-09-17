package com.queuewise.authentication.web;

import com.queuewise.authentication.application.AuthService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Public registration and login")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
        @Operation(summary = "Register a customer", description = "Creates an active customer account and returns a bearer JWT. Authentication is not required. Public registration cannot choose elevated roles.")
        @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Customer registered", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponse.class), examples = @ExampleObject(value = "{\"accessToken\":\"eyJ...\",\"tokenType\":\"Bearer\",\"expiresAt\":\"2026-09-17T10:30:00Z\"}"))),
            @ApiResponse(responseCode = "400", description = "Invalid request fields", content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "Organization not found", content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
            @ApiResponse(responseCode = "409", description = "Contact already registered", content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse")))
        })
            ResponseEntity<AuthResponse> register(
                @Valid
                @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Customer registration payload. Password is write-only and is never returned or logged.",
                    content = @Content(schema = @Schema(implementation = RegisterRequest.class), examples = @ExampleObject(value = "{\"organizationId\":\"uuid\",\"firstName\":\"Maya\",\"lastName\":\"Patel\",\"email\":\"maya@example.com\",\"password\":\"long-enough-password\"}")))
                @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
        @Operation(summary = "Log in", description = "Authenticates an active user by organization and email or phone, then returns a short-lived bearer JWT. Authentication is not required.")
        @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponse.class), examples = @ExampleObject(value = "{\"accessToken\":\"eyJ...\",\"tokenType\":\"Bearer\",\"expiresAt\":\"2026-09-17T10:30:00Z\"}"))),
            @ApiResponse(responseCode = "400", description = "Invalid request fields", content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse")))
        })
            ResponseEntity<AuthResponse> login(
                @Valid
                @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Organization-scoped login credentials. The password is write-only.",
                    content = @Content(schema = @Schema(implementation = LoginRequest.class), examples = @ExampleObject(value = "{\"organizationId\":\"uuid\",\"login\":\"maya@example.com\",\"password\":\"long-enough-password\"}")))
                @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
