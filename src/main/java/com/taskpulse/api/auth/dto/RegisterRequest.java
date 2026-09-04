package com.taskpulse.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating an account.
 *
 * @param email       login identifier; stored lower-cased and must be unused
 * @param displayName name shown in the UI
 * @param password    plaintext password; only ever stored as a BCrypt hash
 */
@Schema(name = "RegisterRequest", description = "Payload for creating an account.")
public record RegisterRequest(

		@NotBlank(message = "Email is required")
		@Email(message = "Email must be a valid address")
		@Size(max = 190, message = "Email must be at most 190 characters")
		@Schema(description = "Login email. Stored lower-cased and must not already be registered.",
				example = "sam@example.com", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 190)
		String email,

		@NotBlank(message = "Display name is required")
		@Size(max = 100, message = "Display name must be at most 100 characters")
		@Schema(description = "Name shown in the UI.", example = "Sam Rivera",
				requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100)
		String displayName,

		// The upper bound is not cosmetic: BCrypt silently ignores anything past 72 bytes,
		// so a longer password would be accepted here and then only partly checked at login.
		@NotBlank(message = "Password is required")
		@Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
		@Schema(description = "Password, 8 to 72 characters.", example = "correct-horse-battery",
				requiredMode = Schema.RequiredMode.REQUIRED, minLength = 8, maxLength = 72)
		String password) {
}
