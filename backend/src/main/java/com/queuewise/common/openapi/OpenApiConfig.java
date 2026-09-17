package com.queuewise.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Global OpenAPI configuration for QueueWise.
 *
 * <h3>Access</h3>
 * <ul>
 *   <li>Swagger UI: {@code /swagger-ui.html}</li>
 *   <li>OpenAPI JSON: {@code /v3/api-docs}</li>
 * </ul>
 *
 * <h3>Security scheme</h3>
 * All protected endpoints require the {@code BearerAuth} scheme — a JWT issued by
 * {@code POST /api/auth/login} or {@code POST /api/auth/register}. Pass the token in
 * the {@code Authorization: Bearer <token>} header.
 *
 * <h3>Base-path note</h3>
 * Two base-path conventions exist in this codebase:
 * <ul>
 *   <li>{@code /api/...} — authentication, audit, counter operations</li>
 *   <li>{@code /api/v1/...} — analytics (pre-existing convention)</li>
 * </ul>
 * This inconsistency is tracked as a technical debt item and will be unified in a
 * future release.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI queueWiseOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("QueueWise API")
                        .description("""
                                QueueWise is an appointment and queue management platform.
                                
                                ## Authentication
                                All protected endpoints require a JWT bearer token. Obtain one via
                                `POST /api/auth/login` or `POST /api/auth/register`, then pass it as:
                                ```
                                Authorization: Bearer <token>
                                ```
                                
                                ## Roles
                                | Role         | Scope                                      |
                                |--------------|--------------------------------------------|
                                | CUSTOMER     | Book appointments, track own tokens        |
                                | RECEPTIONIST | Create walk-in tokens, check-in customers  |
                                | STAFF        | Operate counter, call/complete tokens      |
                                | SUPERVISOR   | All STAFF actions + audit log access       |
                                | ADMIN        | Full access including analytics            |
                                
                                ## Correlation IDs
                                Every request may include an `X-Correlation-Id` header. If provided,
                                the same ID is echoed back in error responses for distributed tracing.
                                """)
                        .version("0.1.0")
                        .contact(new Contact()
                                .name("QueueWise Engineering")
                                .email("engineering@queuewise.io"))
                        .license(new License()
                                .name("Proprietary")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local development"),
                        new Server().url("https://api.queuewise.io").description("Production")))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT issued by POST /api/auth/login or /api/auth/register")));
    }
}
