package com.taskpulse.api.task.dto;

import java.time.LocalDate;
import java.util.List;

import com.taskpulse.api.tag.dto.TagResponse;
import com.taskpulse.api.task.TaskPriority;
import com.taskpulse.api.task.TaskStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Everything the dashboard needs, in one response.
 *
 * <p>Deliberately a single call: the tiles, the breakdowns and the trend all describe the
 * same moment, and fetching them separately would let the numbers disagree with each other
 * mid-render.</p>
 *
 * @param total          every task the caller owns
 * @param open           tasks not yet done
 * @param inProgress     tasks in the IN_PROGRESS column
 * @param completed      tasks in the DONE column
 * @param overdue        open tasks whose due date has passed
 * @param dueToday       open tasks due today
 * @param dueNext7Days   open tasks due within the coming week, today excluded
 * @param noDueDate      open tasks with no deadline
 * @param completionRate percentage of all tasks that are done, rounded to one decimal
 * @param byStatus       counts per workflow column
 * @param openByPriority counts per urgency, over open tasks only
 * @param trend          per-day created and completed counts across the requested window
 * @param topTags        the caller's busiest tags, with usage counts
 */
@Schema(name = "TaskStatsResponse", description = "Aggregate task figures backing the dashboard.")
public record TaskStatsResponse(

		@Schema(description = "Every task you own.", example = "42") long total,
		@Schema(description = "Tasks that are not done yet.", example = "17") long open,
		@Schema(description = "Tasks currently in progress.", example = "5") long inProgress,
		@Schema(description = "Tasks that are done.", example = "25") long completed,
		@Schema(description = "Open tasks whose due date has passed.", example = "3") long overdue,
		@Schema(description = "Open tasks due today.", example = "2") long dueToday,
		@Schema(description = "Open tasks due in the next seven days, today excluded.", example = "6")
		long dueNext7Days,
		@Schema(description = "Open tasks with no due date.", example = "4") long noDueDate,

		@Schema(description = "Share of all tasks that are done, as a percentage.", example = "59.5")
		double completionRate,

		@Schema(description = "Counts per workflow column, in workflow order.")
		List<StatusCount> byStatus,

		@Schema(description = "Counts per urgency across open tasks, most urgent first.")
		List<PriorityCount> openByPriority,

		@Schema(description = "Per-day created and completed counts, oldest day first.")
		List<TrendPoint> trend,

		@Schema(description = "Busiest tags, most used first.")
		List<TagResponse> topTags) {

	/**
	 * @param status the column
	 * @param label  display heading
	 * @param count  tasks in it
	 */
	@Schema(name = "StatusCount", description = "How many tasks sit in one workflow column.")
	public record StatusCount(TaskStatus status, String label, long count) {

		public static StatusCount of(TaskStatus status, long count) {
			return new StatusCount(status, status.getLabel(), count);
		}
	}

	/**
	 * @param priority the urgency
	 * @param label    display label
	 * @param count    open tasks at that urgency
	 */
	@Schema(name = "PriorityCount", description = "How many open tasks sit at one urgency.")
	public record PriorityCount(TaskPriority priority, String label, long count) {

		public static PriorityCount of(TaskPriority priority, long count) {
			return new PriorityCount(priority, priority.getLabel(), count);
		}
	}

	/**
	 * One day of the trend chart.
	 *
	 * @param date      the calendar day
	 * @param created   tasks created that day
	 * @param completed tasks completed that day
	 */
	@Schema(name = "TrendPoint", description = "Tasks created and completed on a single day.")
	public record TrendPoint(
			@Schema(description = "The day, yyyy-MM-dd.", example = "2026-09-04",
					type = "string", format = "date")
			LocalDate date,
			@Schema(description = "Tasks created that day.", example = "3") long created,
			@Schema(description = "Tasks completed that day.", example = "5") long completed) {
	}
}
