package com.taskpulse.api.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Web configuration.
 *
 * <p>Opens CORS on {@code /api/**} for the origins listed in the
 * {@code app.cors.allowed-origins} property (comma-separated).</p>
 *
 * <p>This is published as a {@link CorsConfigurationSource} bean rather than through
 * {@code WebMvcConfigurer#addCorsMappings}, because Spring Security's filter chain has to
 * apply the same rules: preflight requests carry no bearer token and would otherwise be
 * rejected before ever reaching the dispatcher servlet. One bean, one source of truth.</p>
 *
 * <p>Credentials stay disabled: the browser sends the token in an {@code Authorization}
 * header, not a cookie, so a wildcard origin remains a valid configuration.</p>
 */
@Configuration
public class WebConfig {

    private static final long MAX_AGE_SECONDS = 3600L;

    private static final List<String> ALLOWED_METHODS =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    private final List<String> allowedOrigins;

    public WebConfig(@Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(ALLOWED_METHODS);
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Location"));
        config.setMaxAge(MAX_AGE_SECONDS);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
