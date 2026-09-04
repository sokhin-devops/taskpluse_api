package com.taskpulse.api.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

/**
 * springdoc-openapi configuration for the TaskPulse API.
 *
 * <p>The generated specification is served at {@code /v3/api-docs} and the
 * interactive Swagger UI at {@code /swagger-ui.html}.</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String LOCAL_SERVER_URL = "http://localhost:8082";

    @Bean
    public OpenAPI taskPulseOpenAPI() {
        Info info = new Info()
                .title("TaskPulse API")
                .version("1.0.0")
                .description("REST API for TaskPulse: create, list, edit, delete and complete tasks.");

        Server localServer = new Server()
                .url(LOCAL_SERVER_URL)
                .description("Local development server");

        return new OpenAPI()
                .info(info)
                .servers(List.of(localServer));
    }
}
