package com.taskpulse.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The result of a successful register or login.
 *
 * <p>The account is returned alongside the token so the client can render the signed-in
 * user without a follow-up request.</p>
 *
 * @param token     the bearer token to send in the Authorization header
 * @param tokenType always {@code Bearer}, spelled out so clients need not hardcode it
 * @param expiresIn how many seconds the token stays valid
 * @param user      the authenticated account
 */
@Schema(name = "AuthResponse", description = "A bearer token plus the account it belongs to.")
public record AuthResponse(

		@Schema(description = "Send this as: Authorization: Bearer <token>",
				example = "eyJhbGciOiJIUzI1NiJ9...")
		String token,

		@Schema(description = "Token scheme.", example = "Bearer")
		String tokenType,

		@Schema(description = "Lifetime of the token in seconds.", example = "43200")
		long expiresIn,

		@Schema(description = "The authenticated account.")
		UserResponse user) {

	public static AuthResponse of(String token, long expiresInSeconds, UserResponse user) {
		return new AuthResponse(token, "Bearer", expiresInSeconds, user);
	}
}
