package com.taskpulse.api.task.dto;

import java.util.List;

import com.taskpulse.api.task.TaskStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The kanban board: every workflow column with its tasks already in display order.
 *
 * <p>Returned as one payload rather than one request per column so a board never renders
 * half-loaded, and so the client cannot show a task in two columns at once by interleaving
 * responses.</p>
 *
 * @param columns one entry per {@link TaskStatus}, in workflow order, including empty ones
 */
@Schema(name = "BoardResponse", description = "All board columns with their tasks in display order.")
public record BoardResponse(

		@Schema(description = "One entry per workflow status, in order, including columns with no tasks.")
		List<BoardColumn> columns) {

	/**
	 * A single column of the board.
	 *
	 * @param status the workflow status this column represents
	 * @param label  human-readable column heading
	 * @param tasks  the tasks in the column, ordered by position
	 * @param total  how many tasks the column holds
	 */
	@Schema(name = "BoardColumn", description = "One column of the kanban board.")
	public record BoardColumn(

			@Schema(description = "Workflow status this column represents.", example = "IN_PROGRESS")
			TaskStatus status,

			@Schema(description = "Display heading for the column.", example = "In progress")
			String label,

			@Schema(description = "Tasks in the column, ordered by position.")
			List<TaskResponse> tasks,

			@Schema(description = "How many tasks the column holds.", example = "4")
			int total) {

		public static BoardColumn of(TaskStatus status, List<TaskResponse> tasks) {
			return new BoardColumn(status, status.getLabel(), tasks, tasks.size());
		}
	}
}
