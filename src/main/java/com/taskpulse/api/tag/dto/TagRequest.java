package com.taskpulse.api.tag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload used to create or rename a tag.
 *
 * @param name  the label text, unique within the account
 * @param color six-digit hex colour with a leading {@code #}; defaults when omitted
 */
@Schema(name = "TagRequest", description = "Payload used to create or update a tag.")
public record TagRequest(

		@NotBlank(message = "Name is required")
		@Size(max = 40, message = "Name must be at most 40 characters")
		@Schema(description = "Label text. Must be unique within your account.",
				example = "Work", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 40)
		String name,

		// Validated by pattern rather than a colour type so the value that reaches the
		// database can always be dropped straight into CSS.
		@Pattern(regexp = "^#([0-9a-fA-F]{6})$", message = "Color must be a hex value such as #2a78d6")
		@Schema(description = "Six-digit hex colour including the leading '#'. Defaults to blue.",
				example = "#2a78d6", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
		String color) {
}
