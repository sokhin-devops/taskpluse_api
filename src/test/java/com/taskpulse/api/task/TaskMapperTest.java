package com.taskpulse.api.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.taskpulse.api.tag.Tag;
import com.taskpulse.api.tag.dto.TagResponse;
import com.taskpulse.api.task.dto.TaskResponse;

/**
 * Unit tests for {@link TaskMapper}.
 */
class TaskMapperTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 9, 4);

	private final TaskMapper mapper = new TaskMapper();

	private static Task task(LocalDate dueDate, TaskStatus status) {
		Task task = new Task();
		task.setId(1L);
		task.setTitle("Write the report");
		task.setDueDate(dueDate);
		task.setStatus(status);
		task.setPriority(TaskPriority.HIGH);
		return task;
	}

	private static Tag tag(Long id, String name) {
		Tag tag = new Tag(name, "#6366f1", null);
		tag.setId(id);
		return tag;
	}

	@Test
	@DisplayName("flags an open task whose due date has passed as overdue")
	void marksPastDueOpenTaskOverdue() {
		TaskResponse response = mapper.toResponse(task(TODAY.minusDays(1), TaskStatus.TODO), TODAY);

		assertThat(response.overdue()).isTrue();
	}

	@Test
	@DisplayName("does not flag a task due today as overdue")
	void dueTodayIsNotOverdue() {
		assertThat(mapper.toResponse(task(TODAY, TaskStatus.TODO), TODAY).overdue()).isFalse();
	}

	@Test
	@DisplayName("does not flag a completed task as overdue, however late it was finished")
	void completedTaskIsNeverOverdue() {
		TaskResponse response = mapper.toResponse(task(TODAY.minusDays(30), TaskStatus.DONE), TODAY);

		assertThat(response.overdue()).isFalse();
		assertThat(response.completed()).isTrue();
		assertThat(response.completedAt()).isNotNull();
	}

	@Test
	@DisplayName("does not flag a task without a due date as overdue")
	void undatedTaskIsNeverOverdue() {
		assertThat(mapper.toResponse(task(null, TaskStatus.TODO), TODAY).overdue()).isFalse();
	}

	@Test
	@DisplayName("carries the display labels for status and priority")
	void includesDisplayLabels() {
		TaskResponse response = mapper.toResponse(task(TODAY, TaskStatus.IN_PROGRESS), TODAY);

		assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
		assertThat(response.statusLabel()).isEqualTo("In progress");
		assertThat(response.priority()).isEqualTo(TaskPriority.HIGH);
		assertThat(response.priorityLabel()).isEqualTo("High");
	}

	@Test
	@DisplayName("sorts tags by name so the chips do not shuffle between renders")
	void sortsTagsByName() {
		Task task = task(TODAY, TaskStatus.TODO);
		// Insertion order is deliberately not alphabetical.
		task.setTags(new LinkedHashSet<>(Set.of()));
		task.getTags().add(tag(3L, "work"));
		task.getTags().add(tag(1L, "Admin"));
		task.getTags().add(tag(2L, "personal"));

		assertThat(mapper.toResponse(task, TODAY).tags())
				.extracting(TagResponse::name)
				.containsExactly("Admin", "personal", "work");
	}

	@Test
	@DisplayName("omits the usage count on tags nested inside a task")
	void nestedTagsCarryNoCount() {
		Task task = task(TODAY, TaskStatus.TODO);
		task.getTags().add(tag(1L, "Work"));

		assertThat(mapper.toResponse(task, TODAY).tags())
				.singleElement()
				.satisfies(tag -> assertThat(tag.taskCount()).isNull());
	}

	@Test
	@DisplayName("returns an empty tag list rather than null when a task has none")
	void emptyTagsAreAnEmptyList() {
		assertThat(mapper.toResponse(task(TODAY, TaskStatus.TODO), TODAY).tags()).isEmpty();
	}

	@Test
	@DisplayName("maps a null entity to a null response")
	void mapsNullToNull() {
		assertThat(mapper.toResponse(null, TODAY)).isNull();
	}
}
