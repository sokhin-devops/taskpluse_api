package com.taskpulse.api.task.dto;

import java.time.LocalDate;
import java.util.List;

import com.taskpulse.api.task.TaskPriority;
import com.taskpulse.api.task.TaskStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload used to create or update a task.
 *
 * <p>Every field except {@code title} is optional, and an omitted field leaves the current
 * value untouched on update. That is why {@code status}, {@code priority} and
 * {@code completed} are nullable here even though the entity always has a value for
 * them.</p>
 *
 * @param title       short summary; required
 * @param description optional longer details
 * @param dueDate     optional calendar date, {@code yyyy-MM-dd}
 * @param status      workflow column to place the task in
 * @param priority    urgency of the task
 * @param tagIds      the complete set of tags the task should carry after the write
 * @param completed   legacy completion flag, kept for the original API contract
 */
@Schema(name = "TaskRequest", description = "Payload used to create or update a task.")
public record TaskRequest(

		@NotBlank(message = "Title is required")
		@Size(max = 200, message = "Title must be at most 200 characters")
		@Schema(description = "Short summary of the task. Required, 1 to 200 characters.",
				example = "Write the sprint report",
				requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200)
		String title,

		@Size(max = 5000, message = "Description must be at most 5000 characters")
		@Schema(description = "Optional longer details about the task.",
				example = "Summarise velocity, blockers and the demo agenda for Friday.",
				requiredMode = Schema.RequiredMode.NOT_REQUIRED, maxLength = 5000, nullable = true)
		String description,

		@Schema(description = "Optional due date in yyyy-MM-dd format.",
				example = "2026-09-10", type = "string", format = "date",
				requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
		LocalDate dueDate,

		@Schema(description = "Workflow column. Defaults to TODO on create; left untouched when omitted on update.",
				example = "IN_PROGRESS", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
		TaskStatus status,

		@Schema(description = "Urgency. Defaults to MEDIUM on create; left untouched when omitted on update.",
				example = "HIGH", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
		TaskPriority priority,

		@Schema(description = "The full set of tag ids the task should carry after this write. "
				+ "Send an empty array to clear every tag; omit the field to leave them as they are. "
				+ "Ids must belong to your account.",
				example = "[1, 4]", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
		List<Long> tagIds,

		@Schema(description = "Legacy completion flag. Setting it true moves the task to DONE, "
				+ "false back to TODO. Prefer 'status'.",
				example = "false", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
		Boolean completed) {
}
