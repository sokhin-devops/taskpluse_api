package com.taskpulse.api.tag.dto;

import com.taskpulse.api.tag.Tag;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A tag as returned by the API.
 *
 * @param id        generated identifier
 * @param name      label text
 * @param color     six-digit hex colour including the leading {@code #}
 * @param taskCount how many of the caller's tasks carry this tag; {@code null} when the
 *                  tag is being reported inline on a task, where the count is noise
 */
@Schema(name = "TagResponse", description = "A tag as returned by the API.")
public record TagResponse(

		@Schema(description = "Generated identifier of the tag.", example = "1")
		Long id,

		@Schema(description = "Label text.", example = "Work")
		String name,

		@Schema(description = "Six-digit hex colour including the leading '#'.", example = "#6366f1")
		String color,

		@Schema(description = "How many tasks carry this tag. Null when nested inside a task.",
				example = "7", nullable = true)
		Long taskCount) {

	/** The inline form used when a tag appears on a task, where a usage count is noise. */
	public static TagResponse of(Tag tag) {
		return new TagResponse(tag.getId(), tag.getName(), tag.getColor(), null);
	}

	/** The standalone form used by the tag manager and the dashboard. */
	public static TagResponse withCount(Tag tag, long taskCount) {
		return new TagResponse(tag.getId(), tag.getName(), tag.getColor(), taskCount);
	}
}
