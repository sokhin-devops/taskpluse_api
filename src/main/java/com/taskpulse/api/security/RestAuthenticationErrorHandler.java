package com.taskpulse.api.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.taskpulse.api.exception.ApiError;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Renders authentication and authorization failures as {@link ApiError} JSON.
 *
 * <p>These are raised inside the security filter chain, before the dispatcher servlet
 * runs, so {@code GlobalExceptionHandler} never sees them. Without this component the
 * client would get Spring's default HTML error page for a 401 and have to special-case
 * it; with it, every non-2xx response the API can produce has the same shape.</p>
 *
 * <p>The mapper is Jackson 3 ({@code tools.jackson}), which is what Spring Boot 4
 * auto-configures. Jackson 2 is still on the class path as a transitive dependency but has
 * no bean, so importing {@code com.fasterxml.jackson} here would not resolve.</p>
 */
@Component
public class RestAuthenticationErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final ObjectMapper objectMapper;

	public RestAuthenticationErrorHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		write(request, response, HttpStatus.UNAUTHORIZED,
				"Authentication required. Send a valid Bearer token.");
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		write(request, response, HttpStatus.FORBIDDEN,
				"You do not have permission to access this resource.");
	}

	private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message)
			throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		ApiError body = new ApiError(status.value(), status.getReasonPhrase(), message, request.getRequestURI());
		objectMapper.writeValue(response.getOutputStream(), body);
	}
}
