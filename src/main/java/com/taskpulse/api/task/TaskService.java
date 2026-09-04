package com.taskpulse.api.task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taskpulse.api.common.PageResponse;
import com.taskpulse.api.exception.TaskNotFoundException;
import com.taskpulse.api.security.CurrentUser;
import com.taskpulse.api.tag.TagService;
import com.taskpulse.api.task.dto.BoardResponse;
import com.taskpulse.api.task.dto.BoardResponse.BoardColumn;
import com.taskpulse.api.task.dto.TaskMoveRequest;
import com.taskpulse.api.task.dto.TaskRequest;
import com.taskpulse.api.task.dto.TaskResponse;
import com.taskpulse.api.task.dto.TaskStatsResponse;
import com.taskpulse.api.task.dto.TaskStatsResponse.PriorityCount;
import com.taskpulse.api.task.dto.TaskStatsResponse.StatusCount;
import com.taskpulse.api.task.dto.TaskStatsResponse.TrendPoint;
import com.taskpulse.api.user.User;

/**
 * Application service for the task use cases: list, board, dashboard and CRUD.
 *
 * <p>All persistence access goes through {@link TaskRepository}; the controller only ever
 * sees DTOs produced by {@link TaskMapper}. Every method scopes its work to the account
 * resolved from {@link CurrentUser}, so one caller can never see or move another's task.</p>
 */
@Service
@Transactional
public class TaskService {

	/** Default width of the dashboard trend window, in days. */
	public static final int DEFAULT_TREND_DAYS = 14;

	/** Widest trend window a caller may ask for. */
	public static final int MAX_TREND_DAYS = 90;

	/** How many tags the dashboard shows in its "busiest tags" list. */
	private static final int TOP_TAGS = 5;

	private final TaskRepository repository;
	private final TaskMapper mapper;
	private final TagService tagService;
	private final CurrentUser currentUser;

	public TaskService(TaskRepository repository, TaskMapper mapper, TagService tagService,
			CurrentUser currentUser) {
		this.repository = repository;
		this.mapper = mapper;
		this.tagService = tagService;
		this.currentUser = currentUser;
	}

	// -------------------------------------------------------------------- read

	/**
	 * Lists the caller's tasks, filtered, sorted and paged on the database side.
	 *
	 * @param query the validated request parameters
	 * @return one page of matching tasks
	 */
	@Transactional(readOnly = true)
	public PageResponse<TaskResponse> findAll(TaskQuery query) {
		Long ownerId = currentUser.requireId();
		LocalDate today = LocalDate.now();
		Page<Task> page = repository.findAll(
				TaskSpecifications.from(query, ownerId, today), query.toPageable());
		return PageResponse.of(page, task -> mapper.toResponse(task, today));
	}

	@Transactional(readOnly = true)
	public TaskResponse findById(Long id) {
		return mapper.toResponse(getOrThrow(id));
	}

	/**
	 * The whole kanban board in one response.
	 *
	 * @return every workflow column, in order, each holding its tasks by position
	 */
	@Transactional(readOnly = true)
	public BoardResponse board() {
		Long ownerId = currentUser.requireId();
		LocalDate today = LocalDate.now();

		// One query for the board, grouped in memory: three targeted queries would cost
		// three round trips to produce exactly the same rows.
		Map<TaskStatus, List<TaskResponse>> grouped = new EnumMap<>(TaskStatus.class);
		for (TaskStatus status : TaskStatus.values()) {
			grouped.put(status, new ArrayList<>());
		}
		for (Task task : repository.findAllByOwnerIdOrderByStatusWeightAscPositionAscIdDesc(ownerId)) {
			grouped.get(task.getStatus()).add(mapper.toResponse(task, today));
		}

		List<BoardColumn> columns = new ArrayList<>();
		for (TaskStatus status : TaskStatus.values()) {
			columns.add(BoardColumn.of(status, List.copyOf(grouped.get(status))));
		}
		return new BoardResponse(List.copyOf(columns));
	}

	/**
	 * Aggregate figures for the dashboard.
	 *
	 * @param requestedDays width of the trend window; clamped to 1..{@value #MAX_TREND_DAYS}
	 * @return the counts, breakdowns and daily trend, all as of the same moment
	 */
	@Transactional(readOnly = true)
	public TaskStatsResponse stats(int requestedDays) {
		Long ownerId = currentUser.requireId();
		LocalDate today = LocalDate.now();
		int days = clamp(requestedDays, 1, MAX_TREND_DAYS);

		Map<TaskStatus, Long> byStatus = statusCounts(ownerId);
		long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
		long completed = byStatus.getOrDefault(TaskStatus.DONE, 0L);

		return new TaskStatsResponse(
				total,
				total - completed,
				byStatus.getOrDefault(TaskStatus.IN_PROGRESS, 0L),
				completed,
				repository.countByOwnerIdAndCompletedFalseAndDueDateBefore(ownerId, today),
				repository.countByOwnerIdAndCompletedFalseAndDueDate(ownerId, today),
				repository.countByOwnerIdAndCompletedFalseAndDueDateBetween(
						ownerId, today.plusDays(1), today.plusDays(7)),
				repository.countByOwnerIdAndCompletedFalseAndDueDateIsNull(ownerId),
				completionRate(completed, total),
				toStatusCounts(byStatus),
				openPriorityCounts(ownerId),
				trend(ownerId, today, days),
				tagService.findTopUsed(TOP_TAGS));
	}

	// ------------------------------------------------------------------- write

	public TaskResponse create(TaskRequest request) {
		User owner = currentUser.requireEntity();
		Task task = new Task();
		task.setOwner(owner);
		// A new task lands at the top of its column, which is where the person who just
		// typed it expects to find it.
		task.setPosition(0);
		apply(request, task, owner.getId(), true);
		Task saved = repository.save(task);
		shiftColumnAfterInsert(owner.getId(), saved);
		return mapper.toResponse(saved);
	}

	public TaskResponse update(Long id, TaskRequest request) {
		Long ownerId = currentUser.requireId();
		Task task = getOrThrow(id);
		apply(request, task, ownerId, false);
		return mapper.toResponse(repository.save(task));
	}

	/**
	 * Flips a task between DONE and TODO.
	 *
	 * <p>Kept for the original {@code PATCH /{id}/complete} contract and the checkbox in
	 * the list view. The board uses {@link #move} instead, which can also express
	 * "in progress" and a position.</p>
	 */
	public TaskResponse toggleComplete(Long id) {
		Task task = getOrThrow(id);
		task.setCompleted(!task.isCompleted());
		return mapper.toResponse(repository.save(task));
	}

	/**
	 * Applies a board drag: moves a task to a column and to an index within it.
	 *
	 * <p>Both affected columns are renumbered from 0 so positions stay contiguous. The task
	 * is removed from the fetched lists by id before being reinserted, because Hibernate may
	 * flush the status change before the column query runs and would otherwise return the
	 * task in its new column already — inserting it again would list it twice.</p>
	 *
	 * @param id      the task being dragged
	 * @param request target column and index; an index past the end appends
	 * @return the task in its new place
	 */
	public TaskResponse move(Long id, TaskMoveRequest request) {
		Long ownerId = currentUser.requireId();
		Task task = getOrThrow(id);

		TaskStatus from = task.getStatus();
		TaskStatus to = request.status();

		List<Task> source = mutableColumn(ownerId, from);
		List<Task> target = from == to ? source : mutableColumn(ownerId, to);
		source.removeIf(other -> Objects.equals(other.getId(), id));
		if (target != source) {
			target.removeIf(other -> Objects.equals(other.getId(), id));
		}

		task.setStatus(to);

		int index = request.position() == null
				? target.size()
				: clamp(request.position(), 0, target.size());
		target.add(index, task);

		renumber(target);
		repository.saveAll(target);
		if (target != source) {
			renumber(source);
			repository.saveAll(source);
		}
		return mapper.toResponse(task);
	}

	public void delete(Long id) {
		Task task = getOrThrow(id);
		repository.delete(task);
	}

	// ---------------------------------------------------------------- internal

	/**
	 * Copies the writable fields of a request onto an entity.
	 *
	 * <p>An absent field means "leave this alone", which is what makes a partial update
	 * safe. On create the entity defaults stand in instead. {@code status} wins over the
	 * legacy {@code completed} flag when a caller sends both, since it is the more
	 * specific instruction.</p>
	 *
	 * @param request  the incoming payload
	 * @param task     the entity to mutate
	 * @param ownerId  the account tag ids must belong to
	 * @param creating whether this is a create rather than an update
	 */
	private void apply(TaskRequest request, Task task, Long ownerId, boolean creating) {
		task.setTitle(request.title().trim());
		task.setDescription(blankToNull(request.description()));
		task.setDueDate(request.dueDate());

		if (request.priority() != null) {
			task.setPriority(request.priority());
		}
		if (request.status() != null) {
			task.setStatus(request.status());
		}
		else if (request.completed() != null) {
			task.setCompleted(request.completed());
		}

		// null means "don't touch the tags"; an empty list means "clear them".
		if (request.tagIds() != null) {
			task.setTags(tagService.resolveForOwner(request.tagIds(), ownerId));
		}
		else if (creating) {
			task.setTags(Set.of());
		}
	}

	/**
	 * Makes room for a task that was just inserted at the top of its column by pushing the
	 * tasks that were already there down by one.
	 */
	private void shiftColumnAfterInsert(Long ownerId, Task inserted) {
		List<Task> column = mutableColumn(ownerId, inserted.getStatus());
		column.removeIf(other -> Objects.equals(other.getId(), inserted.getId()));
		column.add(0, inserted);
		renumber(column);
		repository.saveAll(column);
	}

	private List<Task> mutableColumn(Long ownerId, TaskStatus status) {
		return new ArrayList<>(repository.findAllByOwnerIdAndStatusOrderByPositionAscIdDesc(ownerId, status));
	}

	private static void renumber(List<Task> column) {
		for (int index = 0; index < column.size(); index++) {
			column.get(index).setPosition(index);
		}
	}

	private Task getOrThrow(Long id) {
		return repository.findByIdAndOwnerId(id, currentUser.requireId())
				.orElseThrow(() -> new TaskNotFoundException(id));
	}

	private static String blankToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	// -------------------------------------------------------------- statistics

	private Map<TaskStatus, Long> statusCounts(Long ownerId) {
		Map<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
		for (TaskStatus status : TaskStatus.values()) {
			counts.put(status, 0L);
		}
		for (TaskRepository.StatusCount row : repository.countGroupedByStatus(ownerId)) {
			counts.put(row.getStatus(), row.getTotal());
		}
		return counts;
	}

	private static List<StatusCount> toStatusCounts(Map<TaskStatus, Long> byStatus) {
		List<StatusCount> counts = new ArrayList<>();
		for (TaskStatus status : TaskStatus.values()) {
			counts.add(StatusCount.of(status, byStatus.getOrDefault(status, 0L)));
		}
		return List.copyOf(counts);
	}

	/** Ordered most urgent first, and always covering every priority so the chart is stable. */
	private List<PriorityCount> openPriorityCounts(Long ownerId) {
		Map<TaskPriority, Long> counts = new EnumMap<>(TaskPriority.class);
		for (TaskPriority priority : TaskPriority.values()) {
			counts.put(priority, 0L);
		}
		for (TaskRepository.PriorityCount row : repository.countOpenGroupedByPriority(ownerId)) {
			counts.put(row.getPriority(), row.getTotal());
		}

		List<TaskPriority> mostUrgentFirst = new ArrayList<>(List.of(TaskPriority.values()));
		mostUrgentFirst.sort((left, right) -> Integer.compare(right.getWeight(), left.getWeight()));
		return mostUrgentFirst.stream()
				.map(priority -> PriorityCount.of(priority, counts.get(priority)))
				.toList();
	}

	/**
	 * Builds the per-day created/completed series.
	 *
	 * <p>Bucketing happens here rather than in SQL because date truncation is spelled
	 * differently on every database, and the window is bounded so the row count is small.
	 * Days with no activity are emitted as zeroes: a chart with gaps in its x-axis
	 * misrepresents a quiet week as a short one.</p>
	 */
	private List<TrendPoint> trend(Long ownerId, LocalDate today, int days) {
		LocalDate start = today.minusDays(days - 1L);
		LocalDateTime since = start.atStartOfDay();

		Map<LocalDate, Long> created = bucketByDay(repository.findCreatedAtSince(ownerId, since));
		Map<LocalDate, Long> completed = bucketByDay(repository.findCompletedAtSince(ownerId, since));

		List<TrendPoint> points = new ArrayList<>(days);
		for (int offset = 0; offset < days; offset++) {
			LocalDate day = start.plusDays(offset);
			points.add(new TrendPoint(day,
					created.getOrDefault(day, 0L),
					completed.getOrDefault(day, 0L)));
		}
		return List.copyOf(points);
	}

	private static Map<LocalDate, Long> bucketByDay(List<LocalDateTime> timestamps) {
		Map<LocalDate, Long> byDay = new HashMap<>();
		for (LocalDateTime timestamp : timestamps) {
			if (timestamp != null) {
				byDay.merge(timestamp.toLocalDate(), 1L, Long::sum);
			}
		}
		return byDay;
	}

	/**
	 * Constrains a value to a range.
	 *
	 * <p>Hand-rolled because {@code Math.clamp} arrived in Java 21 and this module targets
	 * 17; see the {@code java.version} property in the POM.</p>
	 */
	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	/** Rounded to one decimal so the dashboard does not render 59.499999999999996%. */
	private static double completionRate(long completed, long total) {
		if (total == 0) {
			return 0d;
		}
		return Math.round(completed * 1000d / total) / 10d;
	}
}
