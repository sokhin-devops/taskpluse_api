package com.taskpulse.api.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * springdoc-openapi configuration for the TaskPulse API.
 *
 * <p>The generated specification is served at {@code /v3/api-docs} and the
 * interactive Swagger UI at {@code /swagger-ui.html}.</p>
 *
 * <p>A global bearer requirement is declared so Swagger UI shows an Authorize button:
 * paste the token from {@code POST /api/auth/login} once and every task and tag
 * operation can be tried from the browser. The two {@code /api/auth} endpoints override
 * this with an empty requirement of their own.</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String LOCAL_SERVER_URL = "http://localhost:8082";

    /** Referenced by {@code @SecurityRequirement(name = ...)} on the controllers. */
    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI taskPulseOpenAPI() {
        Info info = new Info()
                .title("TaskPulse API")
                .version("2.0.0")
                .description("""
                        REST API for TaskPulse: accounts, tasks with priority, tags and a \
                        kanban workflow, plus board and dashboard views.

                        Register or log in through /api/auth to obtain a bearer token, then \
                        press Authorize. All other endpoints are scoped to the signed-in user.""");

        Server localServer = new Server()
                .url(LOCAL_SERVER_URL)
                .description("Local development server");

        SecurityScheme bearer = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Token returned by POST /api/auth/login");

        return new OpenAPI()
                .info(info)
                .servers(List.of(localServer))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, bearer))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
