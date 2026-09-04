package com.taskpulse.api.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.taskpulse.api.auth.dto.AuthResponse;
import com.taskpulse.api.auth.dto.LoginRequest;
import com.taskpulse.api.auth.dto.RegisterRequest;
import com.taskpulse.api.auth.dto.UserResponse;
import com.taskpulse.api.exception.EmailAlreadyUsedException;
import com.taskpulse.api.exception.InvalidCredentialsException;
import com.taskpulse.api.security.AppUserDetailsService;
import com.taskpulse.api.security.JwtService;

/**
 * Web-layer tests for {@link AuthController}.
 *
 * <p>Checks the two things a client depends on: the status code for each outcome, and that
 * no response ever carries a password back out.</p>
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService service;

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private AppUserDetailsService userDetailsService;

	private static AuthResponse authResponse() {
		return AuthResponse.of("a.b.c", 43200L,
				new UserResponse(1L, "sam@example.com", "Sam Rivera",
						LocalDateTime.of(2026, 9, 4, 9, 15)));
	}

	// --------------------------------------------------------------- register

	@Nested
	@DisplayName("POST /api/auth/register")
	class Register {

		@Test
		@DisplayName("returns 201 with the token and the new account")
		void created() throws Exception {
			when(service.register(any(RegisterRequest.class))).thenReturn(authResponse());

			mockMvc.perform(post("/api/auth/register")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{
									  "email": "sam@example.com",
									  "displayName": "Sam Rivera",
									  "password": "correct-horse-battery"
									}"""))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.token").value("a.b.c"))
					.andExpect(jsonPath("$.tokenType").value("Bearer"))
					.andExpect(jsonPath("$.expiresIn").value(43200))
					.andExpect(jsonPath("$.user.email").value("sam@example.com"))
					// The response must never echo a credential back.
					.andExpect(jsonPath("$.user.password").doesNotExist())
					.andExpect(jsonPath("$.user.passwordHash").doesNotExist());
		}

		@Test
		@DisplayName("answers 409 when the email already has an account")
		void duplicateEmail() throws Exception {
			when(service.register(any(RegisterRequest.class)))
					.thenThrow(new EmailAlreadyUsedException("sam@example.com"));

			mockMvc.perform(post("/api/auth/register")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"email":"sam@example.com","displayName":"Sam","password":"password123"}"""))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.status").value(409))
					.andExpect(jsonPath("$.message").value("An account already exists for sam@example.com"));
		}

		@Test
		@DisplayName("answers 400 naming each field that failed validation")
		void validatesEveryField() throws Exception {
			mockMvc.perform(post("/api/auth/register")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"email":"not-an-email","displayName":"","password":"short"}"""))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.status").value(400))
					.andExpect(jsonPath("$.fieldErrors.email").exists())
					.andExpect(jsonPath("$.fieldErrors.displayName").exists())
					.andExpect(jsonPath("$.fieldErrors.password").exists());

			verify(service, never()).register(any());
		}

		@Test
		@DisplayName("rejects a password longer than BCrypt can actually check")
		void rejectsOverlongPassword() throws Exception {
			String tooLong = "x".repeat(73);

			mockMvc.perform(post("/api/auth/register")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"email":"sam@example.com","displayName":"Sam","password":"%s"}"""
									.formatted(tooLong)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.fieldErrors.password").exists());
		}
	}

	// ------------------------------------------------------------------ login

	@Nested
	@DisplayName("POST /api/auth/login")
	class Login {

		@Test
		@DisplayName("returns 200 with a token")
		void success() throws Exception {
			when(service.login(any(LoginRequest.class))).thenReturn(authResponse());

			mockMvc.perform(post("/api/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"email":"sam@example.com","password":"correct-horse-battery"}"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.token").value("a.b.c"))
					.andExpect(jsonPath("$.user.displayName").value("Sam Rivera"));
		}

		@Test
		@DisplayName("answers 401 without saying which half of the credentials was wrong")
		void badCredentials() throws Exception {
			when(service.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());

			mockMvc.perform(post("/api/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"email":"sam@example.com","password":"wrong"}"""))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.status").value(401))
					.andExpect(jsonPath("$.message").value("Incorrect email or password"));
		}

		@Test
		@DisplayName("answers 400 for empty credentials, without a format rule on the password")
		void requiresBothFields() throws Exception {
			mockMvc.perform(post("/api/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"email":"","password":""}"""))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.fieldErrors.email").value("Email is required"))
					.andExpect(jsonPath("$.fieldErrors.password").value("Password is required"));
		}

		@Test
		@DisplayName("accepts any password shape, so a malformed one looks like a wrong one")
		void doesNotValidatePasswordShapeAtLogin() throws Exception {
			when(service.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());

			mockMvc.perform(post("/api/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"email":"sam@example.com","password":"x"}"""))
					.andExpect(status().isUnauthorized());
		}
	}

	// --------------------------------------------------------------------- me

	@Test
	@DisplayName("GET /api/auth/me returns the signed-in account")
	void me() throws Exception {
		when(service.currentAccount()).thenReturn(
				new UserResponse(1L, "sam@example.com", "Sam Rivera", LocalDateTime.of(2026, 9, 4, 9, 15)));

		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.email").value("sam@example.com"))
				.andExpect(jsonPath("$.displayName").value("Sam Rivera"));
	}
}
