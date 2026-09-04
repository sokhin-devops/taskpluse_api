package com.taskpulse.api.task.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.taskpulse.api.tag.dto.TagResponse;
import com.taskpulse.api.task.TaskPriority;
import com.taskpulse.api.task.TaskStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A task as returned by the API.
 *
 * <p>{@code statusLabel}, {@code priorityLabel} and {@code overdue} are conveniences the
 * server computes so that two different clients cannot disagree about them. In particular
 * {@code overdue} depends on "today", and deciding that on the server keeps a stale
 * browser tab from rendering yesterday's answer.</p>
 *
 * @param id            generated identifier
 * @param title         short summary
 * @param description   longer details, or {@code null}
 * @param dueDate       calendar date, or {@code null} when the task has no deadline
 * @param status        workflow column
 * @param statusLabel   human-readable form of {@code status}
 * @param priority      urgency
 * @param priorityLabel human-readable form of {@code priority}
 * @param position      rank of the task inside its board column
 * @param completed     whether the task is finished; always {@code status == DONE}
 * @param completedAt   when the task was finished, or {@code null}
 * @param overdue       whether the task is pending and past its due date
 * @param tags          the labels on this task
 * @param createdAt     creation timestamp
 * @param updatedAt     timestamp of the last change
 */
@Schema(name = "TaskResponse", description = "A task as returned by the API.")
public record TaskResponse(

		@Schema(description = "Generated identifier of the task.", example = "1")
		Long id,

		@Schema(description = "Short summary of the task.", example = "Write the sprint report")
		String title,

		@Schema(description = "Longer details, or null when none was provided.",
				example = "Summarise velocity, blockers and the demo agenda for Friday.", nullable = true)
		String description,

		@Schema(description = "Due date in yyyy-MM-dd format, or null when the task has no deadline.",
				example = "2026-09-10", type = "string", format = "date", nullable = true)
		LocalDate dueDate,

		@Schema(description = "Workflow column the task sits in.", example = "IN_PROGRESS")
		TaskStatus status,

		@Schema(description = "Display label for the status.", example = "In progress")
		String statusLabel,

		@Schema(description = "Urgency of the task.", example = "HIGH")
		TaskPriority priority,

		@Schema(description = "Display label for the priority.", example = "High")
		String priorityLabel,

		@Schema(description = "Rank of the task within its board column, counting from 0.", example = "2")
		int position,

		@Schema(description = "Whether the task is finished. Always equivalent to status == DONE.",
				example = "false")
		boolean completed,

		@Schema(description = "When the task was completed, or null while it is still open.",
				example = "2026-09-04T16:20:00", type = "string", format = "date-time", nullable = true)
		LocalDateTime completedAt,

		@Schema(description = "True when the task is still open and its due date has passed.",
				example = "false")
		boolean overdue,

		@Schema(description = "Tags carried by this task.")
		List<TagResponse> tags,

		@Schema(description = "Timestamp of when the task was created.",
				example = "2026-09-02T10:00:00", type = "string", format = "date-time")
		LocalDateTime createdAt,

		@Schema(description = "Timestamp of the last update to the task.",
				example = "2026-09-02T10:00:00", type = "string", format = "date-time")
		LocalDateTime updatedAt) {
}
