package com.taskpulse.api.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.taskpulse.api.exception.TaskNotFoundException;
import com.taskpulse.api.security.CurrentUser;
import com.taskpulse.api.tag.Tag;
import com.taskpulse.api.tag.TagService;
import com.taskpulse.api.task.dto.TaskMoveRequest;
import com.taskpulse.api.task.dto.TaskRequest;
import com.taskpulse.api.task.dto.TaskResponse;
import com.taskpulse.api.user.User;

/**
 * Unit tests for {@link TaskService}.
 *
 * <p>Focused on the rules the service owns rather than on persistence: what a partial
 * update leaves alone, and how a board move renumbers the columns it touches. The latter is
 * the part most likely to break quietly — a wrong index produces a board that looks right
 * until it is reloaded.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskServiceTest {

	private static final Long OWNER_ID = 7L;

	@Mock
	private TaskRepository repository;

	@Mock
	private TagService tagService;

	@Mock
	private CurrentUser currentUser;

	private TaskService service;

	private User owner;

	@BeforeEach
	void setUp() {
		owner = new User("sam@example.com", "Sam Rivera", "hash");
		owner.setId(OWNER_ID);

		service = new TaskService(repository, new TaskMapper(), tagService, currentUser);

		when(currentUser.requireId()).thenReturn(OWNER_ID);
		when(currentUser.requireEntity()).thenReturn(owner);
		// Default: saving returns whatever was handed in.
		when(repository.save(any(Task.class))).thenAnswer(call -> call.getArgument(0));
		when(repository.saveAll(any())).thenAnswer(call -> call.getArgument(0));
	}

	// ------------------------------------------------------------------ helpers

	private Task existing(Long id, TaskStatus status, int position) {
		Task task = new Task();
		task.setId(id);
		task.setTitle("Task " + id);
		task.setOwner(owner);
		task.setStatus(status);
		task.setPosition(position);
		when(repository.findByIdAndOwnerId(id, OWNER_ID)).thenReturn(Optional.of(task));
		return task;
	}

	private void columnContains(TaskStatus status, Task... tasks) {
		when(repository.findAllByOwnerIdAndStatusOrderByPositionAscIdDesc(OWNER_ID, status))
				.thenReturn(new ArrayList<>(List.of(tasks)));
	}

	private static TaskRequest request(String title) {
		return new TaskRequest(title, null, null, null, null, null, null);
	}

	// ------------------------------------------------------------------- create

	@Nested
	@DisplayName("create")
	class Create {

		@Test
		@DisplayName("assigns the signed-in account as the owner")
		void setsOwner() {
			columnContains(TaskStatus.TODO);

			service.create(request("Write the report"));

			ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
			verify(repository).save(saved.capture());
			assertThat(saved.getValue().getOwner()).isSameAs(owner);
		}

		@Test
		@DisplayName("trims the title and turns a blank description into null")
		void normalisesText() {
			columnContains(TaskStatus.TODO);

			TaskResponse response = service.create(
					new TaskRequest("  Write the report  ", "   ", null, null, null, null, null));

			assertThat(response.title()).isEqualTo("Write the report");
			assertThat(response.description()).isNull();
		}

		@Test
		@DisplayName("lands at the top of its column and pushes the rest down")
		void insertsAtTheTop() {
			Task first = existing(1L, TaskStatus.TODO, 0);
			Task second = existing(2L, TaskStatus.TODO, 1);
			columnContains(TaskStatus.TODO, first, second);

			service.create(request("Newest"));

			// The new task takes position 0; the two that were there shift to 1 and 2.
			assertThat(first.getPosition()).isEqualTo(1);
			assertThat(second.getPosition()).isEqualTo(2);
		}

		@Test
		@DisplayName("resolves supplied tag ids against the caller's own tags")
		void resolvesTags() {
			columnContains(TaskStatus.TODO);
			Tag work = new Tag("Work", "#6366f1", owner);
			work.setId(3L);
			when(tagService.resolveForOwner(List.of(3L), OWNER_ID)).thenReturn(Set.of(work));

			TaskResponse response = service.create(
					new TaskRequest("Tagged", null, null, null, null, List.of(3L), null));

			assertThat(response.tags()).extracting("name").containsExactly("Work");
			verify(tagService).resolveForOwner(List.of(3L), OWNER_ID);
		}

		@Test
		@DisplayName("honours an explicit status and priority")
		void appliesStatusAndPriority() {
			columnContains(TaskStatus.IN_PROGRESS);

			TaskResponse response = service.create(new TaskRequest("Started", null, null,
					TaskStatus.IN_PROGRESS, TaskPriority.URGENT, null, null));

			assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
			assertThat(response.priority()).isEqualTo(TaskPriority.URGENT);
		}
	}

	// ------------------------------------------------------------------- update

	@Nested
	@DisplayName("update")
	class Update {

		@Test
		@DisplayName("leaves priority, status and tags alone when the request omits them")
		void partialUpdateLeavesOmittedFieldsAlone() {
			Task task = existing(1L, TaskStatus.IN_PROGRESS, 0);
			task.setPriority(TaskPriority.URGENT);
			Tag work = new Tag("Work", "#6366f1", owner);
			work.setId(3L);
			task.getTags().add(work);

			TaskResponse response = service.update(1L, request("Renamed"));

			assertThat(response.title()).isEqualTo("Renamed");
			assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
			assertThat(response.priority()).isEqualTo(TaskPriority.URGENT);
			assertThat(response.tags()).hasSize(1);
			verify(tagService, never()).resolveForOwner(any(), anyLong());
		}

		@Test
		@DisplayName("clears every tag when sent an empty list")
		void emptyTagListClearsTags() {
			Task task = existing(1L, TaskStatus.TODO, 0);
			Tag work = new Tag("Work", "#6366f1", owner);
			work.setId(3L);
			task.getTags().add(work);
			when(tagService.resolveForOwner(List.of(), OWNER_ID)).thenReturn(Set.of());

			TaskResponse response = service.update(1L,
					new TaskRequest("Untagged", null, null, null, null, List.of(), null));

			assertThat(response.tags()).isEmpty();
		}

		@Test
		@DisplayName("prefers an explicit status over the legacy completed flag")
		void statusWinsOverCompletedFlag() {
			existing(1L, TaskStatus.TODO, 0);

			TaskResponse response = service.update(1L, new TaskRequest("Task", null, null,
					TaskStatus.IN_PROGRESS, null, null, Boolean.TRUE));

			assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
			assertThat(response.completed()).isFalse();
		}

		@Test
		@DisplayName("still honours the completed flag when no status is given")
		void completedFlagUsedWhenStatusAbsent() {
			existing(1L, TaskStatus.TODO, 0);

			TaskResponse response = service.update(1L,
					new TaskRequest("Task", null, null, null, null, null, Boolean.TRUE));

			assertThat(response.status()).isEqualTo(TaskStatus.DONE);
			assertThat(response.completed()).isTrue();
		}

		@Test
		@DisplayName("reports a task belonging to somebody else as not found")
		void unknownIdIsNotFound() {
			when(repository.findByIdAndOwnerId(99L, OWNER_ID)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.update(99L, request("Nope")))
					.isInstanceOf(TaskNotFoundException.class)
					.hasMessageContaining("99");
		}
	}

	// --------------------------------------------------------------------- move

	@Nested
	@DisplayName("move")
	class Move {

		@Test
		@DisplayName("renumbers both columns when a task changes column")
		void acrossColumns() {
			Task moving = existing(1L, TaskStatus.TODO, 0);
			Task staying = existing(2L, TaskStatus.TODO, 1);
			Task target = existing(3L, TaskStatus.IN_PROGRESS, 0);
			columnContains(TaskStatus.TODO, moving, staying);
			columnContains(TaskStatus.IN_PROGRESS, target);

			service.move(1L, new TaskMoveRequest(TaskStatus.IN_PROGRESS, 0));

			assertThat(moving.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
			assertThat(moving.getPosition()).isZero();
			// The task it displaced moves down, and the source column closes its gap.
			assertThat(target.getPosition()).isEqualTo(1);
			assertThat(staying.getPosition()).isZero();
		}

		@Test
		@DisplayName("reorders within one column without duplicating the task")
		void withinOneColumn() {
			Task first = existing(1L, TaskStatus.TODO, 0);
			Task second = existing(2L, TaskStatus.TODO, 1);
			Task third = existing(3L, TaskStatus.TODO, 2);
			columnContains(TaskStatus.TODO, first, second, third);

			service.move(1L, new TaskMoveRequest(TaskStatus.TODO, 2));

			assertThat(second.getPosition()).isZero();
			assertThat(third.getPosition()).isEqualTo(1);
			assertThat(first.getPosition()).isEqualTo(2);
		}

		@Test
		@DisplayName("appends when no position is given")
		void nullPositionAppends() {
			Task moving = existing(1L, TaskStatus.TODO, 0);
			Task a = existing(2L, TaskStatus.DONE, 0);
			Task b = existing(3L, TaskStatus.DONE, 1);
			columnContains(TaskStatus.TODO, moving);
			columnContains(TaskStatus.DONE, a, b);

			service.move(1L, new TaskMoveRequest(TaskStatus.DONE, null));

			assertThat(moving.getPosition()).isEqualTo(2);
		}

		@Test
		@DisplayName("clamps a position past the end of the column")
		void positionBeyondEndIsClamped() {
			Task moving = existing(1L, TaskStatus.TODO, 0);
			Task other = existing(2L, TaskStatus.DONE, 0);
			columnContains(TaskStatus.TODO, moving);
			columnContains(TaskStatus.DONE, other);

			service.move(1L, new TaskMoveRequest(TaskStatus.DONE, 99));

			assertThat(moving.getPosition()).isEqualTo(1);
		}

		@Test
		@DisplayName("does not list the task twice if the repository already reports it moved")
		void toleratesAnEarlyFlush() {
			Task moving = existing(1L, TaskStatus.TODO, 0);
			Task other = existing(2L, TaskStatus.IN_PROGRESS, 0);
			columnContains(TaskStatus.TODO, moving);
			// Simulates Hibernate flushing the status change before the column query runs,
			// so the task comes back as part of its *new* column too.
			columnContains(TaskStatus.IN_PROGRESS, moving, other);

			service.move(1L, new TaskMoveRequest(TaskStatus.IN_PROGRESS, 0));

			assertThat(moving.getPosition()).isZero();
			assertThat(other.getPosition()).isEqualTo(1);
		}

		@Test
		@DisplayName("marks a task completed when it is dragged into Done")
		void draggingToDoneCompletesIt() {
			Task moving = existing(1L, TaskStatus.TODO, 0);
			columnContains(TaskStatus.TODO, moving);
			columnContains(TaskStatus.DONE);

			TaskResponse response = service.move(1L, new TaskMoveRequest(TaskStatus.DONE, 0));

			assertThat(response.completed()).isTrue();
			assertThat(response.completedAt()).isNotNull();
		}
	}

	// -------------------------------------------------------------------- other

	@Test
	@DisplayName("toggling completion flips a task between done and to-do")
	void toggleComplete() {
		Task task = existing(1L, TaskStatus.TODO, 0);

		assertThat(service.toggleComplete(1L).status()).isEqualTo(TaskStatus.DONE);
		assertThat(task.isCompleted()).isTrue();

		assertThat(service.toggleComplete(1L).status()).isEqualTo(TaskStatus.TODO);
		assertThat(task.isCompleted()).isFalse();
	}

	@Test
	@DisplayName("deleting scopes the lookup to the owner before removing anything")
	void deleteIsOwnerScoped() {
		Task task = existing(1L, TaskStatus.TODO, 0);

		service.delete(1L);

		verify(repository).findByIdAndOwnerId(1L, OWNER_ID);
		verify(repository).delete(task);
	}

	@Test
	@DisplayName("deleting a task that is not yours removes nothing")
	void deleteUnknownIdDeletesNothing() {
		when(repository.findByIdAndOwnerId(99L, OWNER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(TaskNotFoundException.class);

		verify(repository, never()).delete(any(Task.class));
	}

	@Test
	@DisplayName("the board returns one column per status, even the empty ones")
	void boardAlwaysHasEveryColumn() {
		Task todo = existing(1L, TaskStatus.TODO, 0);
		when(repository.findAllByOwnerIdOrderByStatusWeightAscPositionAscIdDesc(OWNER_ID))
				.thenReturn(List.of(todo));

		var board = service.board();

		assertThat(board.columns()).hasSize(TaskStatus.values().length);
		assertThat(board.columns()).extracting("status")
				.containsExactly(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.DONE);
		assertThat(board.columns().get(0).tasks()).hasSize(1);
		assertThat(board.columns().get(1).tasks()).isEmpty();
		assertThat(board.columns().get(2).total()).isZero();
	}

	// --------------------------------------------------------------- statistics

	@Nested
	@DisplayName("stats")
	class Stats {

		@Test
		@DisplayName("derives the open count and completion rate from the status breakdown")
		void derivesTotals() {
			when(repository.countGroupedByStatus(OWNER_ID)).thenReturn(List.of(
					statusRow(TaskStatus.TODO, 6),
					statusRow(TaskStatus.IN_PROGRESS, 2),
					statusRow(TaskStatus.DONE, 2)));

			var stats = service.stats(7);

			assertThat(stats.total()).isEqualTo(10);
			assertThat(stats.open()).isEqualTo(8);
			assertThat(stats.inProgress()).isEqualTo(2);
			assertThat(stats.completed()).isEqualTo(2);
			assertThat(stats.completionRate()).isEqualTo(20.0);
		}

		@Test
		@DisplayName("reports a zero completion rate for an empty account rather than dividing by zero")
		void handlesEmptyAccount() {
			when(repository.countGroupedByStatus(OWNER_ID)).thenReturn(List.of());

			var stats = service.stats(7);

			assertThat(stats.total()).isZero();
			assertThat(stats.completionRate()).isZero();
			assertThat(stats.byStatus()).hasSize(3);
		}

		@Test
		@DisplayName("emits one trend point per day in the window, including quiet days")
		void trendCoversEveryDay() {
			when(repository.countGroupedByStatus(OWNER_ID)).thenReturn(List.of());

			var stats = service.stats(5);

			assertThat(stats.trend()).hasSize(5);
			assertThat(stats.trend()).allSatisfy(point -> {
				assertThat(point.created()).isZero();
				assertThat(point.completed()).isZero();
			});
			assertThat(stats.trend().get(4).date()).isEqualTo(LocalDate.now());
		}

		@Test
		@DisplayName("clamps an absurd window instead of building a huge series")
		void clampsTheWindow() {
			when(repository.countGroupedByStatus(OWNER_ID)).thenReturn(List.of());

			assertThat(service.stats(10_000).trend()).hasSize(TaskService.MAX_TREND_DAYS);
			assertThat(service.stats(0).trend()).hasSize(1);
			assertThat(service.stats(-5).trend()).hasSize(1);
		}

		@Test
		@DisplayName("orders the priority breakdown most urgent first and covers every level")
		void priorityBreakdownIsCompleteAndOrdered() {
			when(repository.countGroupedByStatus(OWNER_ID)).thenReturn(List.of());
			when(repository.countOpenGroupedByPriority(OWNER_ID))
					.thenReturn(List.of(priorityRow(TaskPriority.HIGH, 3)));

			var counts = service.stats(7).openByPriority();

			assertThat(counts).extracting("priority").containsExactly(
					TaskPriority.URGENT, TaskPriority.HIGH, TaskPriority.MEDIUM, TaskPriority.LOW);
			assertThat(counts.get(1).count()).isEqualTo(3);
			assertThat(counts.get(0).count()).isZero();
		}

		@Test
		@DisplayName("asks for the due-date tiles as of today")
		void queriesDueDateTilesForToday() {
			when(repository.countGroupedByStatus(OWNER_ID)).thenReturn(List.of());
			LocalDate today = LocalDate.now();

			service.stats(7);

			verify(repository).countByOwnerIdAndCompletedFalseAndDueDateBefore(OWNER_ID, today);
			verify(repository).countByOwnerIdAndCompletedFalseAndDueDate(OWNER_ID, today);
			verify(repository).countByOwnerIdAndCompletedFalseAndDueDateBetween(
					OWNER_ID, today.plusDays(1), today.plusDays(7));
		}
	}

	private static TaskRepository.StatusCount statusRow(TaskStatus status, long total) {
		return new TaskRepository.StatusCount() {
			@Override
			public TaskStatus getStatus() {
				return status;
			}

			@Override
			public long getTotal() {
				return total;
			}
		};
	}

	private static TaskRepository.PriorityCount priorityRow(TaskPriority priority, long total) {
		return new TaskRepository.PriorityCount() {
			@Override
			public TaskPriority getPriority() {
				return priority;
			}

			@Override
			public long getTotal() {
				return total;
			}
		};
	}
}
