package com.taskpulse.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Credentials presented at sign-in.
 *
 * <p>No format or length rules here on purpose. Validating the shape of a submitted
 * password would answer "is this even a password we could have issued?", which is a hint
 * an attacker can use; a wrong password and a malformed one should look identical.</p>
 *
 * @param email    the account email, matched case-insensitively
 * @param password the plaintext password, compared against the stored hash
 */
@Schema(name = "LoginRequest", description = "Credentials presented at sign-in.")
public record LoginRequest(

		@NotBlank(message = "Email is required")
		@Schema(description = "Account email.", example = "sam@example.com",
				requiredMode = Schema.RequiredMode.REQUIRED)
		String email,

		@NotBlank(message = "Password is required")
		@Schema(description = "Account password.", example = "correct-horse-battery",
				requiredMode = Schema.RequiredMode.REQUIRED)
		String password) {
}
