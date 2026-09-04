package com.taskpulse.api.task;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.taskpulse.api.tag.Tag;
import com.taskpulse.api.tag.dto.TagResponse;
import com.taskpulse.api.task.dto.TaskResponse;

/**
 * Converts a {@link Task} entity into its transport representation.
 *
 * <p>Only the read direction lives here. Writes go through {@code TaskService}, because
 * applying a request needs to resolve tag ids against the caller's account and reorder a
 * board column — collaborators a mapper has no business holding.</p>
 */
@Component
public class TaskMapper {

	/**
	 * Maps a persisted task onto the JSON response shape.
	 *
	 * @param task  the entity to convert
	 * @param today the caller's current date, used to decide {@code overdue}
	 * @return the response DTO, or {@code null} when {@code task} is {@code null}
	 */
	public TaskResponse toResponse(Task task, LocalDate today) {
		if (task == null) {
			return null;
		}
		return new TaskResponse(
				task.getId(),
				task.getTitle(),
				task.getDescription(),
				task.getDueDate(),
				task.getStatus(),
				task.getStatus().getLabel(),
				task.getPriority(),
				task.getPriority().getLabel(),
				task.getPosition(),
				task.isCompleted(),
				task.getCompletedAt(),
				isOverdue(task, today),
				toTagResponses(task.getTags()),
				task.getCreatedAt(),
				task.getUpdatedAt());
	}

	/** Convenience for callers that just want "as of today". */
	public TaskResponse toResponse(Task task) {
		return toResponse(task, LocalDate.now());
	}

	/**
	 * Tags are held in a {@code Set}, whose iteration order is not a contract. Sorting by
	 * name here means the chips under a task do not shuffle between two renders of the
	 * same data.
	 */
	private static List<TagResponse> toTagResponses(Iterable<Tag> tags) {
		if (tags == null) {
			return List.of();
		}
		List<TagResponse> mapped = new ArrayList<>();
		tags.forEach(tag -> mapped.add(TagResponse.of(tag)));
		mapped.sort(Comparator.comparing(TagResponse::name, String.CASE_INSENSITIVE_ORDER));
		return List.copyOf(mapped);
	}

	/**
	 * A task is overdue only while it is still open: finishing something late does not
	 * leave it permanently flagged. A task with no due date can never be overdue.
	 */
	private static boolean isOverdue(Task task, LocalDate today) {
		return !task.isCompleted() && task.getDueDate() != null && task.getDueDate().isBefore(today);
	}
}
