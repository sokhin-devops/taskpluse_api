package com.taskpulse.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless bearer-token security for the TaskPulse API.
 *
 * <p>
 * There is deliberately no {@code AuthenticationManager} here. The only
 * credential
 * check in the application happens once, in {@code AuthService#login}, which
 * compares the
 * submitted password against the stored hash with {@link PasswordEncoder}
 * directly. Every
 * later request is authenticated by {@link JwtAuthenticationFilter} instead, so
 * wiring up
 * a provider chain would add moving parts that nothing calls.
 * </p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	/** Paths that must stay reachable without a token. */
	private static final String[] PUBLIC_PATHS = {
			"/api/auth/register",
			"/api/auth/login",
			"/v3/api-docs",
			"/v3/api-docs/**",
			"/swagger-ui.html",
			"/swagger-ui/**",
			"/error",
			"/auth/**",
			"/v3/api-docs/**",
			"/actuator/health"
	};

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final RestAuthenticationErrorHandler errorHandler;

	public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
			RestAuthenticationErrorHandler errorHandler) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.errorHandler = errorHandler;
	}

	@Bean
	public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
		return http
				// CORS uses the CorsConfigurationSource bean declared in WebConfig.
				.cors(Customizer.withDefaults())
				// No cookies or sessions are used, so there is no CSRF surface to protect.
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						// Preflight carries no Authorization header of its own.
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

						.requestMatchers(PUBLIC_PATHS).permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(errorHandler)
						.accessDeniedHandler(errorHandler))
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
