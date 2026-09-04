package com.taskpulse.api.task.dto;

import com.taskpulse.api.task.TaskStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Payload for a board move: which column a task lands in, and where in that column.
 *
 * @param status   the column the task is dropped into
 * @param position zero-based index within that column; {@code null} appends to the end
 */
@Schema(name = "TaskMoveRequest",
		description = "Where to place a task on the board after a drag: target column and index within it.")
public record TaskMoveRequest(

		@NotNull(message = "Status is required")
		@Schema(description = "The board column the task is moved into.",
				example = "IN_PROGRESS", requiredMode = Schema.RequiredMode.REQUIRED)
		TaskStatus status,

		@PositiveOrZero(message = "Position must be zero or greater")
		@Schema(description = "Zero-based index within the target column. "
				+ "Omit, or send a value past the end, to append.",
				example = "0", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
		Integer position) {
}
