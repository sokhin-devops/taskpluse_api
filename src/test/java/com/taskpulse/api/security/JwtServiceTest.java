package com.taskpulse.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.taskpulse.api.user.User;

/**
 * Unit tests for {@link JwtService}.
 *
 * <p>The rejection cases matter more than the happy path here: a token that is accepted
 * when it should not be is the difference between an API that is scoped per account and one
 * that only looks like it is.</p>
 */
class JwtServiceTest {

	private static final String SECRET = "unit-test-secret-key-long-enough-for-hs256-0123";
	private static final String OTHER_SECRET = "a-completely-different-secret-key-also-long-0123";

	private final JwtService jwtService = new JwtService(SECRET, 60);

	private static User user(Long id, String email) {
		User user = new User(email, "Sam Rivera", "irrelevant-hash");
		user.setId(id);
		return user;
	}

	@Test
	@DisplayName("issues a token that verifies back to the user id it was created for")
	void roundTripsTheUserId() {
		String token = jwtService.issue(user(42L, "sam@example.com"));

		assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
	}

	@Test
	@DisplayName("reports the configured lifetime in seconds")
	void reportsExpiry() {
		assertThat(jwtService.getExpiresInSeconds()).isEqualTo(3600L);
	}

	@Nested
	@DisplayName("rejects")
	class Rejects {

		@Test
		@DisplayName("a token whose payload has been edited")
		void tamperedToken() {
			String token = jwtService.issue(user(1L, "sam@example.com"));
			String[] parts = token.split("\\.");
			// Re-sign nothing: swap the payload for another user id and keep the old signature.
			String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "XX." + parts[2];

			assertThat(jwtService.extractUserId(tampered)).isNull();
		}

		@Test
		@DisplayName("a token signed with a different secret")
		void wrongSigningKey() {
			String foreignToken = new JwtService(OTHER_SECRET, 60).issue(user(7L, "eve@example.com"));

			assertThat(jwtService.extractUserId(foreignToken)).isNull();
		}

		@Test
		@DisplayName("a token that has already expired")
		void expiredToken() {
			// A negative lifetime puts the expiry in the past the moment it is issued.
			String stale = new JwtService(SECRET, -1).issue(user(1L, "sam@example.com"));

			assertThat(jwtService.extractUserId(stale)).isNull();
		}

		@Test
		@DisplayName("a string that is not a JWT at all")
		void garbage() {
			assertThat(jwtService.extractUserId("not-a-token")).isNull();
			assertThat(jwtService.extractUserId("")).isNull();
		}
	}

	@Test
	@DisplayName("refuses to start with a secret too short for HS256")
	void rejectsShortSecret() {
		assertThatThrownBy(() -> new JwtService("too-short", 60))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("at least 32");
	}
}
