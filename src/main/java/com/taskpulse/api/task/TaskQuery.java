package com.taskpulse.api.task;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * The parsed and validated parameters of {@code GET /api/tasks}.
 *
 * <p>Sorting is deliberately not passed straight through to Spring Data. An unchecked
 * {@code sort} parameter lets a caller order by any mapped property, including ones on
 * related entities, which both leaks the schema and invites accidental cartesian joins.
 * {@link #SORTABLE_FIELDS} is the allow-list of what may be sorted on.</p>
 *
 * <p>That map also does one piece of translation: the public names {@code priority} and
 * {@code status} point at the numeric {@code priorityWeight} and {@code statusWeight}
 * columns. Both enums are persisted as strings, so ordering on the columns they name
 * directly would sort alphabetically — HIGH before LOW, DONE before TODO.</p>
 *
 * @param q          free-text needle matched against title and description
 * @param status     statuses to include; empty means all
 * @param priority   priorities to include; empty means all
 * @param tagIds     match tasks carrying any of these tags; empty means all
 * @param untagged   when true, return only tasks with no tags (overrides {@code tagIds})
 * @param dueFrom    earliest due date to include
 * @param dueTo      latest due date to include
 * @param overdue    when true, keep only pending tasks whose due date has passed
 * @param hasDueDate when set, keep only tasks that do ({@code true}) or do not have a due date
 * @param page       zero-based page index
 * @param size       page size, capped at {@link #MAX_PAGE_SIZE}
 * @param sort       one of {@link #SORTABLE_FIELDS}
 * @param direction  {@code asc} or {@code desc}
 */
public record TaskQuery(
		String q,
		Set<TaskStatus> status,
		Set<TaskPriority> priority,
		List<Long> tagIds,
		boolean untagged,
		LocalDate dueFrom,
		LocalDate dueTo,
		boolean overdue,
		Boolean hasDueDate,
		int page,
		int size,
		String sort,
		String direction) {

	public static final int DEFAULT_PAGE_SIZE = 10;

	/** Upper bound on {@code size}, so one request cannot ask for the whole table. */
	public static final int MAX_PAGE_SIZE = 200;

	public static final String DEFAULT_SORT = "dueDate";

	/** Public sort name to entity property. Anything outside this map is rejected. */
	public static final Map<String, String> SORTABLE_FIELDS = Map.of(
			"title", "title",
			"dueDate", "dueDate",
			"priority", "priorityWeight",
			"status", "statusWeight",
			"createdAt", "createdAt",
			"updatedAt", "updatedAt",
			"position", "position");

	/**
	 * Normalises the raw values bound from the query string.
	 *
	 * @throws IllegalArgumentException when {@code sort} is not an allowed field, which the
	 *         global handler turns into a 400 naming the valid options
	 */
	public TaskQuery {
		if (page < 0) {
			throw new IllegalArgumentException("Parameter 'page' must not be negative");
		}
		if (size < 1) {
			size = DEFAULT_PAGE_SIZE;
		}
		size = Math.min(size, MAX_PAGE_SIZE);

		sort = (sort == null || sort.isBlank()) ? DEFAULT_SORT : sort.trim();
		if (!SORTABLE_FIELDS.containsKey(sort)) {
			throw new IllegalArgumentException("Parameter 'sort' must be one of "
					+ SORTABLE_FIELDS.keySet().stream().sorted().toList() + "; got '" + sort + "'");
		}
		direction = "desc".equalsIgnoreCase(direction) ? "desc" : "asc";

		status = status == null ? Set.of() : Set.copyOf(status);
		priority = priority == null ? Set.of() : Set.copyOf(priority);
		tagIds = tagIds == null ? List.of() : List.copyOf(tagIds);
	}

	/**
	 * @return the paging and ordering request for Spring Data
	 */
	public Pageable toPageable() {
		Sort.Direction dir = "desc".equals(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
		String property = SORTABLE_FIELDS.get(sort);

		// Tasks with no due date sort after those that have one, whichever way the column
		// is ordered; otherwise "earliest first" would lead with a block of undated tasks.
		Sort ordering = "dueDate".equals(property)
				? Sort.by(new Sort.Order(dir, property, Sort.NullHandling.NULLS_LAST))
				: Sort.by(dir, property);

		// A stable tie-break keeps paging deterministic when the sort column has duplicates.
		return PageRequest.of(page, size, ordering.and(Sort.by(Sort.Direction.DESC, "id")));
	}
}
