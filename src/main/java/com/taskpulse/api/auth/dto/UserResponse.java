package com.taskpulse.api.auth.dto;

import java.time.LocalDateTime;

import com.taskpulse.api.user.User;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A user account as returned by the API. The password hash is absent by construction:
 * there is no component to hold it, so no future edit can leak it by accident.
 *
 * @param id          generated identifier
 * @param email       login identifier, lower-cased
 * @param displayName name shown in the UI
 * @param createdAt   when the account was created
 */
@Schema(name = "UserResponse", description = "The signed-in account. Never includes credentials.")
public record UserResponse(

		@Schema(description = "Generated identifier of the account.", example = "1")
		Long id,

		@Schema(description = "Login email, stored lower-cased.", example = "sam@example.com")
		String email,

		@Schema(description = "Name shown in the UI.", example = "Sam Rivera")
		String displayName,

		@Schema(description = "When the account was created.", example = "2026-09-04T09:15:00",
				type = "string", format = "date-time")
		LocalDateTime createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt());
	}
}
