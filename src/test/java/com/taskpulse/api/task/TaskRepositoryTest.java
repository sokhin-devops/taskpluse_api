package com.taskpulse.api.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import com.taskpulse.api.tag.Tag;
import com.taskpulse.api.tag.TagRepository;
import com.taskpulse.api.user.User;
import com.taskpulse.api.user.UserRepository;

import jakarta.persistence.EntityManager;

/**
 * Persistence tests for {@link TaskRepository} and {@link TaskSpecifications}.
 *
 * <p>These run against the real JPA stack on in-memory H2, because the things being checked
 * — that a filter reaches SQL correctly, that a tag join does not double-count a row, that
 * NULL due dates sort last — cannot be observed from a mock.</p>
 *
 * <p>{@code replace = NONE} keeps the datasource declared by the {@code test} profile
 * instead of substituting a default embedded one. That profile runs H2 in PostgreSQL
 * compatibility mode, which the entities need for their {@code TEXT} column and their
 * {@code default '...'} column definitions.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class TaskRepositoryTest {

	@Autowired
	private TaskRepository tasks;

	@Autowired
	private TagRepository tags;

	@Autowired
	private UserRepository users;

	@Autowired
	private EntityManager entityManager;

	private User sam;
	private User other;
	private Tag work;
	private Tag home;

	@BeforeEach
	void setUp() {
		sam = users.save(new User("sam@example.com", "Sam Rivera", "hash"));
		other = users.save(new User("eve@example.com", "Eve Stone", "hash"));
		work = tags.save(new Tag("Work", "#6366f1", sam));
		home = tags.save(new Tag("Home", "#10b981", sam));
	}

	// ----------------------------------------------------------------- helpers

	private Task save(User owner, String title, TaskStatus status, TaskPriority priority,
			LocalDate dueDate, Tag... taskTags) {
		Task task = new Task();
		task.setOwner(owner);
		task.setTitle(title);
		task.setStatus(status);
		task.setPriority(priority);
		task.setDueDate(dueDate);
		task.setTags(Set.of(taskTags));
		return tasks.saveAndFlush(task);
	}

	/** Builds a query with only the filters a test cares about set. */
	private static TaskQuery filter(String q, Set<TaskStatus> status, Set<TaskPriority> priority,
			List<Long> tagIds, boolean untagged, LocalDate dueFrom, LocalDate dueTo,
			boolean overdue, Boolean hasDueDate, String sort, String direction) {
		return new TaskQuery(q, status, priority, tagIds, untagged, dueFrom, dueTo,
				overdue, hasDueDate, 0, 50, sort, direction);
	}

	private List<String> titlesMatching(TaskQuery query) {
		return tasks.findAll(TaskSpecifications.from(query, sam.getId(), LocalDate.now()),
						query.toPageable())
				.getContent().stream()
				.map(Task::getTitle)
				.toList();
	}

	// ----------------------------------------------------------- owner scoping

	@Nested
	@DisplayName("owner scoping")
	class OwnerScoping {

		@Test
		@DisplayName("does not resolve a task belonging to another account")
		void lookupIsScoped() {
			Task theirs = save(other, "Their task", TaskStatus.TODO, TaskPriority.LOW, null);

			assertThat(tasks.findByIdAndOwnerId(theirs.getId(), sam.getId())).isEmpty();
			assertThat(tasks.findByIdAndOwnerId(theirs.getId(), other.getId())).isPresent();
		}

		@Test
		@DisplayName("leaves another account's tasks out of the filtered list")
		void listIsScoped() {
			save(sam, "Mine", TaskStatus.TODO, TaskPriority.LOW, null);
			save(other, "Theirs", TaskStatus.TODO, TaskPriority.LOW, null);

			assertThat(titlesMatching(filter(null, Set.of(), Set.of(), List.of(), false,
					null, null, false, null, "title", "asc"))).containsExactly("Mine");
		}

		@Test
		@DisplayName("counts only the caller's tasks in the dashboard aggregates")
		void aggregatesAreScoped() {
			save(sam, "Mine", TaskStatus.DONE, TaskPriority.LOW, null);
			save(other, "Theirs", TaskStatus.DONE, TaskPriority.LOW, null);

			assertThat(tasks.countByOwnerId(sam.getId())).isEqualTo(1);
			assertThat(tasks.countGroupedByStatus(sam.getId()))
					.singleElement()
					.satisfies(row -> assertThat(row.getTotal()).isEqualTo(1));
		}
	}

	// ---------------------------------------------------------------- filtering

	@Nested
	@DisplayName("filters")
	class Filters {

		@Test
		@DisplayName("match title and description case-insensitively")
		void freeText() {
			save(sam, "Write the REPORT", TaskStatus.TODO, TaskPriority.LOW, null);
			Task withDescription = save(sam, "Something else", TaskStatus.TODO, TaskPriority.LOW, null);
			withDescription.setDescription("mentions a report in the body");
			tasks.saveAndFlush(withDescription);
			save(sam, "Unrelated", TaskStatus.TODO, TaskPriority.LOW, null);

			assertThat(titlesMatching(filter("report", Set.of(), Set.of(), List.of(), false,
					null, null, false, null, "title", "asc")))
					.containsExactly("Something else", "Write the REPORT");
		}

		@Test
		@DisplayName("tolerate a task with no description when searching text")
		void freeTextWithNullDescription() {
			save(sam, "Has no description", TaskStatus.TODO, TaskPriority.LOW, null);

			assertThat(titlesMatching(filter("description", Set.of(), Set.of(), List.of(), false,
					null, null, false, null, "title", "asc"))).hasSize(1);
		}

		@Test
		@DisplayName("keep only the requested statuses")
		void byStatus() {
			save(sam, "Todo", TaskStatus.TODO, TaskPriority.LOW, null);
			save(sam, "Doing", TaskStatus.IN_PROGRESS, TaskPriority.LOW, null);
			save(sam, "Done", TaskStatus.DONE, TaskPriority.LOW, null);

			assertThat(titlesMatching(filter(null, Set.of(TaskStatus.TODO, TaskStatus.DONE),
					Set.of(), List.of(), false, null, null, false, null, "title", "asc")))
					.containsExactly("Done", "Todo");
		}

		@Test
		@DisplayName("keep only the requested priorities")
		void byPriority() {
			save(sam, "Low", TaskStatus.TODO, TaskPriority.LOW, null);
			save(sam, "Urgent", TaskStatus.TODO, TaskPriority.URGENT, null);

			assertThat(titlesMatching(filter(null, Set.of(), Set.of(TaskPriority.URGENT),
					List.of(), false, null, null, false, null, "title", "asc")))
					.containsExactly("Urgent");
		}

		@Test
		@DisplayName("match a task carrying any of the requested tags")
		void byAnyTag() {
			save(sam, "Work only", TaskStatus.TODO, TaskPriority.LOW, null, work);
			save(sam, "Home only", TaskStatus.TODO, TaskPriority.LOW, null, home);
			save(sam, "Untagged", TaskStatus.TODO, TaskPriority.LOW, null);

			assertThat(titlesMatching(filter(null, Set.of(), Set.of(), List.of(work.getId()),
					false, null, null, false, null, "title", "asc")))
					.containsExactly("Work only");
		}

		@Test
		@DisplayName("return a task carrying two matching tags only once")
		void tagJoinDoesNotDuplicateRows() {
			save(sam, "Both tags", TaskStatus.TODO, TaskPriority.LOW, null, work, home);

			TaskQuery query = filter(null, Set.of(), Set.of(),
					List.of(work.getId(), home.getId()), false, null, null, false, null,
					"title", "asc");
			Page<Task> page = tasks.findAll(
					TaskSpecifications.from(query, sam.getId(), LocalDate.now()), query.toPageable());

			assertThat(page.getContent()).hasSize(1);
			// The count query has to agree, or the pager shows a phantom second page.
			assertThat(page.getTotalElements()).isEqualTo(1);
		}

		@Test
		@DisplayName("find tasks with no tags at all")
		void untagged() {
			save(sam, "Tagged", TaskStatus.TODO, TaskPriority.LOW, null, work);
			save(sam, "Bare", TaskStatus.TODO, TaskPriority.LOW, null);

			assertThat(titlesMatching(filter(null, Set.of(), Set.of(), List.of(), true,
					null, null, false, null, "title", "asc"))).containsExactly("Bare");
		}

		@Test
		@DisplayName("restrict to a due-date window")
		void byDueDateRange() {
			save(sam, "Early", TaskStatus.TODO, TaskPriority.LOW, LocalDate.of(2026, 1, 1));
			save(sam, "Inside", TaskStatus.TODO, TaskPriority.LOW, LocalDate.of(2026, 6, 15));
			save(sam, "Late", TaskStatus.TODO, TaskPriority.LOW, LocalDate.of(2026, 12, 31));

			assertThat(titlesMatching(filter(null, Set.of(), Set.of(), List.of(), false,
					LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), false, null,
					"title", "asc"))).containsExactly("Inside");
		}

		@Test
		@DisplayName("treat only open, past-due, dated tasks as overdue")
		void overdue() {
			LocalDate yesterday = LocalDate.now().minusDays(1);
			save(sam, "Open and late", TaskStatus.TODO, TaskPriority.LOW, yesterday);
			save(sam, "Finished late", TaskStatus.DONE, TaskPriority.LOW, yesterday);
			save(sam, "Open, no date", TaskStatus.TODO, TaskPriority.LOW, null);
			save(sam, "Open, future", TaskStatus.TODO, TaskPriority.LOW, LocalDate.now().plusDays(5));

			assertThat(titlesMatching(filter(null, Set.of(), Set.of(), List.of(), false,
					null, null, true, null, "title", "asc"))).containsExactly("Open and late");
		}

		@Test
		@DisplayName("split tasks by whether they have a due date")
		void byDueDatePresence() {
			save(sam, "Dated", TaskStatus.TODO, TaskPriority.LOW, LocalDate.now());
			save(sam, "Undated", TaskStatus.TODO, TaskPriority.LOW, null);

			assertThat(titlesMatching(filter(null, Set.of(), Set.of(), List.of(), false,
					null, null, false, Boolean.TRUE, "title", "asc"))).containsExactly("Dated");
			assertThat(titlesMatching(filter(null, Set.of(), Set.of(), List.of(), false,
					null, null, false, Boolean.FALSE, "title", "asc"))).containsExactly("Undated");
		}

		@Test
		@DisplayName("combine with AND across different filters")
		void filtersCombine() {
			save(sam, "Match", TaskStatus.TODO, TaskPriority.URGENT, null, work);
			save(sam, "Wrong status", TaskStatus.DONE, TaskPriority.URGENT, null, work);
			save(sam, "Wrong priority", TaskStatus.TODO, TaskPriority.LOW, null, work);
			save(sam, "Wrong tag", TaskStatus.TODO, TaskPriority.URGENT, null, home);

			assertThat(titlesMatching(filter(null, Set.of(TaskStatus.TODO),
					Set.of(TaskPriority.URGENT), List.of(work.getId()), false,
					null, null, false, null, "title", "asc"))).containsExactly("Match");
		}
	}

	// ----------------------------------------------------------------- sorting

	@Nested
	@DisplayName("sorting")
	class Sorting {

		@Test
		@DisplayName("orders by urgency, not by the alphabetical name of the priority")
		void byPriorityWeight() {
			save(sam, "Low", TaskStatus.TODO, TaskPriority.LOW, null);
			save(sam, "Urgent", TaskStatus.TODO, TaskPriority.URGENT, null);
			save(sam, "Medium", TaskStatus.TODO, TaskPriority.MEDIUM, null);
			save(sam, "High", TaskStatus.TODO, TaskPriority.HIGH, null);

			assertThat(titlesMatching(filter(null, Set.of(), Set.of(), List.of(), false,
					null, null, false, null, "priority", "desc")))
					.containsExactly("Urgent", "High", "Medium", "Low");
		}

		@Test
		@DisplayName("orders by workflow stage, not by the alphabetical name of the status")
		void byStatusWeight() {
			save(sam, "Done", TaskStatus.DONE, TaskPriority.LOW, null);
			save(sam, "Todo", TaskStatus.TODO, TaskPriority.LOW, null);
			save(sam, "Doing", TaskStatus.IN_PROGRESS, TaskPriority.LOW, null);

			assertThat(titlesMatching(filter(null, Set.of(), Set.of(), List.of(), false,
					null, null, false, null, "status", "asc")))
					.containsExactly("Todo", "Doing", "Done");
		}

		@Test
		@DisplayName("puts undated tasks last in both directions")
		void undatedTasksSortLast() {
			save(sam, "Dated early", TaskStatus.TODO, TaskPriority.LOW, LocalDate.of(2026, 1, 1));
			save(sam, "Undated", TaskStatus.TODO, TaskPriority.LOW, null);
			save(sam, "Dated late", TaskStatus.TODO, TaskPriority.LOW, LocalDate.of(2026, 12, 1));

			assertThat(titlesMatching(filter(null, Set.of(), Set.of(), List.of(), false,
					null, null, false, null, "dueDate", "asc")))
					.containsExactly("Dated early", "Dated late", "Undated");
			assertThat(titlesMatching(filter(null, Set.of(), Set.of(), List.of(), false,
					null, null, false, null, "dueDate", "desc")))
					.containsExactly("Dated late", "Dated early", "Undated");
		}

		@Test
		@DisplayName("pages without repeating a row when the sort column ties")
		void tieBreakKeepsPagingStable() {
			for (int index = 0; index < 6; index++) {
				save(sam, "Same priority", TaskStatus.TODO, TaskPriority.MEDIUM, null);
			}
			TaskQuery query = new TaskQuery(null, Set.of(), Set.of(), List.of(), false,
					null, null, false, null, 0, 3, "priority", "asc");

			Page<Task> first = tasks.findAll(
					TaskSpecifications.from(query, sam.getId(), LocalDate.now()), query.toPageable());
			Page<Task> second = tasks.findAll(
					TaskSpecifications.from(query, sam.getId(), LocalDate.now()),
					PageRequest.of(1, 3, query.toPageable().getSort()));

			assertThat(first.getTotalElements()).isEqualTo(6);
			assertThat(first.getContent()).extracting(Task::getId)
					.doesNotContainAnyElementsOf(second.getContent().stream().map(Task::getId).toList());
		}
	}

	// -------------------------------------------------------------- board order

	@Test
	@DisplayName("returns board columns in workflow order, each ordered by position")
	void boardOrdering() {
		Task second = save(sam, "Second", TaskStatus.TODO, TaskPriority.LOW, null);
		second.setPosition(1);
		Task first = save(sam, "First", TaskStatus.TODO, TaskPriority.LOW, null);
		first.setPosition(0);
		Task doing = save(sam, "Doing", TaskStatus.IN_PROGRESS, TaskPriority.LOW, null);
		doing.setPosition(0);
		Task done = save(sam, "Done", TaskStatus.DONE, TaskPriority.LOW, null);
		done.setPosition(0);
		tasks.flush();

		assertThat(tasks.findAllByOwnerIdAndStatusOrderByPositionAscIdDesc(sam.getId(), TaskStatus.TODO))
				.extracting(Task::getTitle).containsExactly("First", "Second");
		// Workflow order, not the alphabetical DONE / IN_PROGRESS / TODO that the enum's
		// stored string would produce.
		assertThat(tasks.findAllByOwnerIdOrderByStatusWeightAscPositionAscIdDesc(sam.getId()))
				.extracting(Task::getTitle).containsExactly("First", "Second", "Doing", "Done");
	}

	// --------------------------------------------------------------- aggregates

	@Nested
	@DisplayName("dashboard queries")
	class Aggregates {

		@Test
		@DisplayName("count tasks per status")
		void groupByStatus() {
			save(sam, "A", TaskStatus.TODO, TaskPriority.LOW, null);
			save(sam, "B", TaskStatus.TODO, TaskPriority.LOW, null);
			save(sam, "C", TaskStatus.DONE, TaskPriority.LOW, null);

			assertThat(tasks.countGroupedByStatus(sam.getId()))
					.extracting(row -> row.getStatus() + "=" + row.getTotal())
					.containsExactlyInAnyOrder("TODO=2", "DONE=1");
		}

		@Test
		@DisplayName("count open tasks per priority, leaving completed ones out")
		void groupByPriorityIgnoresCompleted() {
			save(sam, "Open urgent", TaskStatus.TODO, TaskPriority.URGENT, null);
			save(sam, "Done urgent", TaskStatus.DONE, TaskPriority.URGENT, null);

			assertThat(tasks.countOpenGroupedByPriority(sam.getId()))
					.singleElement()
					.satisfies(row -> {
						assertThat(row.getPriority()).isEqualTo(TaskPriority.URGENT);
						assertThat(row.getTotal()).isEqualTo(1);
					});
		}

		@Test
		@DisplayName("count due-date tiles as of a given day")
		void dueDateCounts() {
			LocalDate today = LocalDate.now();
			save(sam, "Overdue", TaskStatus.TODO, TaskPriority.LOW, today.minusDays(2));
			save(sam, "Today", TaskStatus.TODO, TaskPriority.LOW, today);
			save(sam, "This week", TaskStatus.TODO, TaskPriority.LOW, today.plusDays(3));
			save(sam, "No date", TaskStatus.TODO, TaskPriority.LOW, null);
			save(sam, "Done and overdue", TaskStatus.DONE, TaskPriority.LOW, today.minusDays(9));

			assertThat(tasks.countByOwnerIdAndCompletedFalseAndDueDateBefore(sam.getId(), today))
					.isEqualTo(1);
			assertThat(tasks.countByOwnerIdAndCompletedFalseAndDueDate(sam.getId(), today))
					.isEqualTo(1);
			assertThat(tasks.countByOwnerIdAndCompletedFalseAndDueDateBetween(
					sam.getId(), today.plusDays(1), today.plusDays(7))).isEqualTo(1);
			assertThat(tasks.countByOwnerIdAndCompletedFalseAndDueDateIsNull(sam.getId()))
					.isEqualTo(1);
		}

		@Test
		@DisplayName("count how many tasks carry each tag")
		void tagUsage() {
			save(sam, "A", TaskStatus.TODO, TaskPriority.LOW, null, work);
			save(sam, "B", TaskStatus.TODO, TaskPriority.LOW, null, work, home);

			assertThat(tasks.countTasksPerTag(sam.getId()))
					.extracting(row -> row.getTagId() + "=" + row.getTaskCount())
					.containsExactlyInAnyOrder(work.getId() + "=2", home.getId() + "=1");
		}

		@Test
		@DisplayName("return creation and completion timestamps inside the trend window")
		void trendTimestamps() {
			save(sam, "Open", TaskStatus.TODO, TaskPriority.LOW, null);
			save(sam, "Finished", TaskStatus.DONE, TaskPriority.LOW, null);
			LocalDateTime since = LocalDate.now().minusDays(1).atStartOfDay();

			assertThat(tasks.findCreatedAtSince(sam.getId(), since)).hasSize(2);
			// Only the completed task has a completion timestamp to report.
			assertThat(tasks.findCompletedAtSince(sam.getId(), since)).hasSize(1);
			assertThat(tasks.findCreatedAtSince(sam.getId(), LocalDateTime.now().plusDays(1))).isEmpty();
		}

		@Test
		@DisplayName("find every task carrying a tag, for the detach-before-delete step")
		void findByTag() {
			Task tagged = save(sam, "Tagged", TaskStatus.TODO, TaskPriority.LOW, null, work);
			save(sam, "Untagged", TaskStatus.TODO, TaskPriority.LOW, null);

			assertThat(tasks.findAllByTagId(work.getId()))
					.extracting(Task::getId).containsExactly(tagged.getId());
		}
	}

	// -------------------------------------------------------------- maintenance

	@Nested
	@DisplayName("start-up repairs")
	class Maintenance {

		@Test
		@DisplayName("move a legacy completed row to DONE")
		void repairsCompletedRowsWithoutStatus() {
			Task legacy = save(sam, "Legacy", TaskStatus.TODO, TaskPriority.LOW, null);
			// Reproduces what ddl-auto=update leaves behind: `completed` was already true,
			// and the freshly added `status` column took its default. The entity setters
			// keep these in step, so the drift has to be forced through raw SQL.
			entityManager.createNativeQuery(
							"update tasks set completed = true, status = 'TODO' where id = :id")
					.setParameter("id", legacy.getId())
					.executeUpdate();
			entityManager.clear();

			assertThat(tasks.markCompletedRowsAsDone()).isEqualTo(1);
			entityManager.clear();

			Task repaired = tasks.findById(legacy.getId()).orElseThrow();
			assertThat(repaired.getStatus()).isEqualTo(TaskStatus.DONE);
			assertThat(repaired.getCompletedAt()).isNotNull();
			// Without this the row would still sort as though it were a to-do.
			assertThat(repaired.getStatusWeight()).isEqualTo(TaskStatus.DONE.getWeight());
		}

		@Test
		@DisplayName("mark a DONE row whose completed flag drifted false")
		void repairsDoneRowsWithoutFlag() {
			Task task = save(sam, "Drifted", TaskStatus.DONE, TaskPriority.LOW, null);
			entityManager.createNativeQuery(
							"update tasks set completed = false where id = :id")
					.setParameter("id", task.getId())
					.executeUpdate();
			entityManager.clear();

			assertThat(tasks.markDoneRowsAsCompleted()).isEqualTo(1);
			entityManager.clear();

			assertThat(tasks.findById(task.getId()).orElseThrow().isCompleted()).isTrue();
		}

		@Test
		@DisplayName("realign a sort weight left behind at its column default")
		void realignsSortWeights() {
			Task task = save(sam, "Stale weights", TaskStatus.IN_PROGRESS, TaskPriority.URGENT, null);
			// Reproduces rows written before the weight columns existed: they took the SQL
			// default rather than a value matching their own status and priority.
			entityManager.createNativeQuery(
							"update tasks set status_weight = 1, priority_weight = 2 where id = :id")
					.setParameter("id", task.getId())
					.executeUpdate();
			entityManager.clear();

			assertThat(tasks.syncStatusWeight(TaskStatus.IN_PROGRESS, TaskStatus.IN_PROGRESS.getWeight()))
					.isEqualTo(1);
			assertThat(tasks.syncPriorityWeight(TaskPriority.URGENT, TaskPriority.URGENT.getWeight()))
					.isEqualTo(1);
			entityManager.clear();

			Task repaired = tasks.findById(task.getId()).orElseThrow();
			assertThat(repaired.getStatusWeight()).isEqualTo(TaskStatus.IN_PROGRESS.getWeight());
			assertThat(repaired.getPriorityWeight()).isEqualTo(TaskPriority.URGENT.getWeight());
		}

		@Test
		@DisplayName("leave a consistent database untouched, so the repairs are safe to repeat")
		void repairsAreIdempotent() {
			save(sam, "Open", TaskStatus.TODO, TaskPriority.LOW, null);
			save(sam, "Closed", TaskStatus.DONE, TaskPriority.LOW, null);

			assertThat(tasks.markCompletedRowsAsDone()).isZero();
			assertThat(tasks.markDoneRowsAsCompleted()).isZero();
			for (TaskStatus status : TaskStatus.values()) {
				assertThat(tasks.syncStatusWeight(status, status.getWeight())).isZero();
			}
			for (TaskPriority priority : TaskPriority.values()) {
				assertThat(tasks.syncPriorityWeight(priority, priority.getWeight())).isZero();
			}
		}

		@Test
		@DisplayName("adopt tasks that have no owner")
		void adoptsOrphans() {
			// An owner-less row cannot be built through the entity: the association is
			// declared non-optional, so Hibernate rejects it before it reaches the database.
			entityManager.createNativeQuery("""
							insert into tasks
							  (title, status, status_weight, priority, priority_weight,
							   position, completed, created_at, updated_at)
							values ('Orphan', 'TODO', 1, 'MEDIUM', 2, 0, false, :now, :now)""")
					.setParameter("now", LocalDateTime.now())
					.executeUpdate();
			entityManager.clear();

			assertThat(tasks.countByOwnerIsNull()).isEqualTo(1);

			assertThat(tasks.assignMissingOwner(sam)).isEqualTo(1);
			entityManager.clear();

			assertThat(tasks.countByOwnerIsNull()).isZero();
			assertThat(tasks.countByOwnerId(sam.getId())).isEqualTo(1);
		}
	}
}
