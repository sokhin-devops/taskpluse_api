package com.taskpulse.api.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the derived-column invariants on {@link Task}.
 *
 * <p>{@code completed}, {@code completedAt}, {@code priorityWeight} and {@code statusWeight}
 * are written by the entity rather than by callers. If those setters drift, the symptoms
 * show up far from the cause: a dashboard trend with no data points, or a board sorted into
 * alphabetical order. These tests pin the invariants down where they are defined.</p>
 */
class TaskTest {

	@Test
	@DisplayName("a new task starts as an open, medium-priority to-do")
	void defaults() {
		Task task = new Task();

		assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
		assertThat(task.getPriority()).isEqualTo(TaskPriority.MEDIUM);
		assertThat(task.getPriorityWeight()).isEqualTo(TaskPriority.MEDIUM.getWeight());
		assertThat(task.getStatusWeight()).isEqualTo(TaskStatus.TODO.getWeight());
		assertThat(task.isCompleted()).isFalse();
		assertThat(task.getCompletedAt()).isNull();
	}

	@Test
	@DisplayName("moving to DONE sets the completion flag and stamps the time")
	void doneImpliesCompleted() {
		Task task = new Task();

		task.setStatus(TaskStatus.DONE);

		assertThat(task.isCompleted()).isTrue();
		assertThat(task.getCompletedAt()).isNotNull();
	}

	@Test
	@DisplayName("IN_PROGRESS is still an open task")
	void inProgressIsNotCompleted() {
		Task task = new Task();

		task.setStatus(TaskStatus.IN_PROGRESS);

		assertThat(task.isCompleted()).isFalse();
		assertThat(task.getCompletedAt()).isNull();
	}

	@Test
	@DisplayName("re-saving a finished task keeps its original completion time")
	void doesNotRewriteCompletionHistory() {
		Task task = new Task();
		task.setStatus(TaskStatus.DONE);
		LocalDateTime firstCompletion = task.getCompletedAt();

		task.setStatus(TaskStatus.DONE);

		assertThat(task.getCompletedAt()).isEqualTo(firstCompletion);
	}

	@Test
	@DisplayName("reopening a task clears its completion time")
	void reopeningClearsCompletion() {
		Task task = new Task();
		task.setStatus(TaskStatus.DONE);

		task.setStatus(TaskStatus.IN_PROGRESS);

		assertThat(task.isCompleted()).isFalse();
		assertThat(task.getCompletedAt()).isNull();
	}

	@Test
	@DisplayName("the legacy completed flag drives the workflow status")
	void completedFlagMovesTheStatus() {
		Task task = new Task();

		task.setCompleted(true);
		assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);

		task.setCompleted(false);
		assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
	}

	@Test
	@DisplayName("a null status falls back to TODO instead of leaving the column empty")
	void nullStatusFallsBack() {
		Task task = new Task();
		task.setStatus(TaskStatus.DONE);

		task.setStatus(null);

		assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
		assertThat(task.isCompleted()).isFalse();
	}

	@Test
	@DisplayName("setting a priority keeps its sortable weight in step")
	void priorityKeepsWeightInSync() {
		Task task = new Task();

		task.setPriority(TaskPriority.URGENT);
		assertThat(task.getPriorityWeight()).isEqualTo(4);

		task.setPriority(TaskPriority.LOW);
		assertThat(task.getPriorityWeight()).isEqualTo(1);
	}

	@Test
	@DisplayName("a null priority falls back to MEDIUM")
	void nullPriorityFallsBack() {
		Task task = new Task();
		task.setPriority(TaskPriority.URGENT);

		task.setPriority(null);

		assertThat(task.getPriority()).isEqualTo(TaskPriority.MEDIUM);
		assertThat(task.getPriorityWeight()).isEqualTo(TaskPriority.MEDIUM.getWeight());
	}

	@Test
	@DisplayName("setting a status keeps its sortable weight in step")
	void statusKeepsWeightInSync() {
		Task task = new Task();

		task.setStatus(TaskStatus.DONE);
		assertThat(task.getStatusWeight()).isEqualTo(TaskStatus.DONE.getWeight());

		task.setStatus(TaskStatus.IN_PROGRESS);
		assertThat(task.getStatusWeight()).isEqualTo(TaskStatus.IN_PROGRESS.getWeight());

		task.setStatus(null);
		assertThat(task.getStatusWeight()).isEqualTo(TaskStatus.TODO.getWeight());
	}

	@Test
	@DisplayName("status weights rank the workflow in the intended order")
	void statusWeightsAreOrdered() {
		assertThat(TaskStatus.TODO.getWeight()).isLessThan(TaskStatus.IN_PROGRESS.getWeight());
		assertThat(TaskStatus.IN_PROGRESS.getWeight()).isLessThan(TaskStatus.DONE.getWeight());
	}

	@Test
	@DisplayName("priority weights rank the constants in the intended order")
	void priorityWeightsAreOrdered() {
		assertThat(TaskPriority.LOW.getWeight())
				.isLessThan(TaskPriority.MEDIUM.getWeight());
		assertThat(TaskPriority.MEDIUM.getWeight())
				.isLessThan(TaskPriority.HIGH.getWeight());
		assertThat(TaskPriority.HIGH.getWeight())
				.isLessThan(TaskPriority.URGENT.getWeight());
	}

	@Test
	@DisplayName("replacing the tag set mutates the tracked collection in place")
	void setTagsReusesTheCollection() {
		Task task = new Task();
		Object original = task.getTags();

		task.setTags(null);

		assertThat(task.getTags()).isSameAs(original).isEmpty();
	}
}
