package com.taskpulse.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.taskpulse.api.auth.dto.AuthResponse;
import com.taskpulse.api.auth.dto.LoginRequest;
import com.taskpulse.api.auth.dto.RegisterRequest;
import com.taskpulse.api.auth.dto.UserResponse;
import com.taskpulse.api.exception.EmailAlreadyUsedException;
import com.taskpulse.api.exception.InvalidCredentialsException;
import com.taskpulse.api.security.CurrentUser;
import com.taskpulse.api.security.JwtService;
import com.taskpulse.api.tag.TagService;
import com.taskpulse.api.user.User;
import com.taskpulse.api.user.UserRepository;

/**
 * Unit tests for {@link AuthService}.
 *
 * <p>Uses a real {@link BCryptPasswordEncoder} rather than a mock: the point of most of
 * these tests is that a password is hashed on the way in and compared as a hash on the way
 * back, and a stubbed encoder would let a plaintext regression pass.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

	@Mock
	private UserRepository users;

	@Mock
	private TagService tagService;

	@Mock
	private CurrentUser currentUser;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	private final JwtService jwtService =
			new JwtService("auth-service-test-secret-key-long-enough-0123456789", 60);

	private AuthService service;

	@BeforeEach
	void setUp() {
		service = new AuthService(users, passwordEncoder, jwtService, tagService, currentUser);
		when(users.save(any(User.class))).thenAnswer(call -> {
			User saved = call.getArgument(0);
			saved.setId(1L);
			return saved;
		});
	}

	private static RegisterRequest registration(String email) {
		return new RegisterRequest(email, "Sam Rivera", "correct-horse-battery");
	}

	// ---------------------------------------------------------------- register

	@Test
	@DisplayName("stores the password as a hash, never as plaintext")
	void hashesThePassword() {
		service.register(registration("sam@example.com"));

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(users).save(saved.capture());
		String hash = saved.getValue().getPasswordHash();

		assertThat(hash).isNotEqualTo("correct-horse-battery").startsWith("$2");
		assertThat(passwordEncoder.matches("correct-horse-battery", hash)).isTrue();
	}

	@Test
	@DisplayName("lower-cases and trims the email so lookups are case-insensitive")
	void normalisesTheEmail() {
		service.register(registration("  SAM@Example.COM  "));

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(users).save(saved.capture());
		assertThat(saved.getValue().getEmail()).isEqualTo("sam@example.com");
	}

	@Test
	@DisplayName("returns a usable token and the new account")
	void returnsTokenAndAccount() {
		AuthResponse response = service.register(registration("sam@example.com"));

		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.expiresIn()).isEqualTo(3600L);
		assertThat(response.user().email()).isEqualTo("sam@example.com");
		assertThat(jwtService.extractUserId(response.token())).isEqualTo(1L);
	}

	@Test
	@DisplayName("seeds the new account with starter tags")
	void seedsStarterTags() {
		service.register(registration("sam@example.com"));

		verify(tagService).createStarterTags(any(User.class));
	}

	@Test
	@DisplayName("rejects an email that already has an account, checking the normalised form")
	void rejectsDuplicateEmail() {
		when(users.existsByEmail("sam@example.com")).thenReturn(true);

		assertThatThrownBy(() -> service.register(registration("SAM@example.com")))
				.isInstanceOf(EmailAlreadyUsedException.class)
				.hasMessageContaining("sam@example.com");

		verify(users, never()).save(any());
	}

	// ------------------------------------------------------------------- login

	@Test
	@DisplayName("issues a token when the password matches")
	void loginSucceeds() {
		User stored = new User("sam@example.com", "Sam Rivera",
				passwordEncoder.encode("correct-horse-battery"));
		stored.setId(42L);
		when(users.findByEmail("sam@example.com")).thenReturn(Optional.of(stored));

		AuthResponse response = service.login(new LoginRequest("sam@example.com", "correct-horse-battery"));

		assertThat(jwtService.extractUserId(response.token())).isEqualTo(42L);
		assertThat(response.user().displayName()).isEqualTo("Sam Rivera");
	}

	@Test
	@DisplayName("accepts a differently-cased email at sign-in")
	void loginIsCaseInsensitive() {
		User stored = new User("sam@example.com", "Sam Rivera",
				passwordEncoder.encode("correct-horse-battery"));
		stored.setId(42L);
		when(users.findByEmail("sam@example.com")).thenReturn(Optional.of(stored));

		assertThat(service.login(new LoginRequest("SAM@Example.com", "correct-horse-battery")))
				.isNotNull();
	}

	@Test
	@DisplayName("rejects a wrong password")
	void rejectsWrongPassword() {
		User stored = new User("sam@example.com", "Sam Rivera",
				passwordEncoder.encode("correct-horse-battery"));
		when(users.findByEmail("sam@example.com")).thenReturn(Optional.of(stored));

		assertThatThrownBy(() -> service.login(new LoginRequest("sam@example.com", "wrong")))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	@DisplayName("gives an unknown email the same error as a wrong password, so accounts cannot be probed")
	void unknownEmailIsIndistinguishableFromWrongPassword() {
		User stored = new User("sam@example.com", "Sam Rivera",
				passwordEncoder.encode("correct-horse-battery"));
		when(users.findByEmail("sam@example.com")).thenReturn(Optional.of(stored));
		when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

		Throwable unknownEmail = catchThrowable(
				() -> service.login(new LoginRequest("nobody@example.com", "whatever")));
		Throwable wrongPassword = catchThrowable(
				() -> service.login(new LoginRequest("sam@example.com", "whatever")));

		assertThat(unknownEmail).isInstanceOf(InvalidCredentialsException.class);
		assertThat(wrongPassword).isInstanceOf(InvalidCredentialsException.class);
		assertThat(unknownEmail.getMessage()).isEqualTo(wrongPassword.getMessage());
	}

	// ---------------------------------------------------------------------- me

	@Test
	@DisplayName("reports the current account without exposing its password hash")
	void currentAccountOmitsCredentials() {
		User stored = new User("sam@example.com", "Sam Rivera", "$2a$10$secret");
		stored.setId(42L);
		when(currentUser.requireEntity()).thenReturn(stored);

		UserResponse response = service.currentAccount();

		assertThat(response.id()).isEqualTo(42L);
		assertThat(response.email()).isEqualTo("sam@example.com");
		// The record has no component for the hash, so this also documents that fact.
		assertThat(response.toString()).doesNotContain("$2a$10$secret");
	}
}
