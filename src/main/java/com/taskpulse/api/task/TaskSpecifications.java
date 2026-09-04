package com.taskpulse.api.task;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.data.jpa.domain.Specification;

import com.taskpulse.api.tag.Tag;

import jakarta.persistence.criteria.JoinType;

/**
 * Composable filters for the task list query.
 *
 * <p>Each method returns one predicate, or {@code null} when the corresponding filter was
 * not supplied. Spring Data treats a {@code null} specification as "no restriction", so
 * {@link Specification#allOf(java.util.List)} can combine them without the caller writing
 * a conditional per filter.</p>
 */
public final class TaskSpecifications {

	private TaskSpecifications() {
	}

	/**
	 * The one non-optional filter. Everything the API returns is scoped to the signed-in
	 * account, and this is where that is enforced for the list query.
	 */
	public static Specification<Task> ownedBy(Long ownerId) {
		return (root, query, cb) -> cb.equal(root.get("owner").get("id"), ownerId);
	}

	/** Case-insensitive substring match across title and description. */
	public static Specification<Task> matchesText(String text) {
		if (isBlank(text)) {
			return null;
		}
		String pattern = "%" + text.trim().toLowerCase() + "%";
		return (root, query, cb) -> cb.or(
				cb.like(cb.lower(root.get("title")), pattern),
				cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern));
	}

	public static Specification<Task> hasStatusIn(Collection<TaskStatus> statuses) {
		if (isEmpty(statuses)) {
			return null;
		}
		return (root, query, cb) -> root.get("status").in(statuses);
	}

	public static Specification<Task> hasPriorityIn(Collection<TaskPriority> priorities) {
		if (isEmpty(priorities)) {
			return null;
		}
		return (root, query, cb) -> root.get("priority").in(priorities);
	}

	/**
	 * Tasks carrying at least one of the given tags.
	 *
	 * <p>The join is marked distinct because a task with two of the requested tags would
	 * otherwise be returned twice — and, worse, counted twice by the paging count query.</p>
	 */
	public static Specification<Task> hasAnyTag(Collection<Long> tagIds) {
		if (isEmpty(tagIds)) {
			return null;
		}
		return (root, query, cb) -> {
			if (query != null) {
				query.distinct(true);
			}
			return root.join("tags", JoinType.INNER).get("id").in(tagIds);
		};
	}

	/** Tasks with no labels at all, which the UI offers as an explicit filter. */
	public static Specification<Task> hasNoTags() {
		return (root, query, cb) -> cb.isEmpty(root.<List<Tag>>get("tags"));
	}

	public static Specification<Task> dueOnOrAfter(LocalDate from) {
		if (from == null) {
			return null;
		}
		return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dueDate"), from);
	}

	public static Specification<Task> dueOnOrBefore(LocalDate to) {
		if (to == null) {
			return null;
		}
		return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("dueDate"), to);
	}

	/**
	 * Pending tasks whose due date has passed. A completed task is never overdue, however
	 * late it was finished, and a task without a due date cannot be.
	 */
	public static Specification<Task> overdueAsOf(LocalDate today) {
		return (root, query, cb) -> cb.and(
				cb.isFalse(root.get("completed")),
				cb.isNotNull(root.get("dueDate")),
				cb.lessThan(root.get("dueDate"), today));
	}

	public static Specification<Task> hasDueDate(boolean present) {
		return (root, query, cb) -> present
				? cb.isNotNull(root.get("dueDate"))
				: cb.isNull(root.get("dueDate"));
	}

	/**
	 * Combines the filters carried by a query object.
	 *
	 * @param query   the parsed request parameters
	 * @param ownerId the account whose tasks are being listed
	 * @param today   the caller's current date, used by the overdue filter
	 * @return a specification matching every supplied filter
	 */
	public static Specification<Task> from(TaskQuery query, Long ownerId, LocalDate today) {
		List<Specification<Task>> parts = new ArrayList<>();
		parts.add(ownedBy(ownerId));
		parts.add(matchesText(query.q()));
		parts.add(hasStatusIn(query.status()));
		parts.add(hasPriorityIn(query.priority()));
		parts.add(query.untagged() ? hasNoTags() : hasAnyTag(query.tagIds()));
		parts.add(dueOnOrAfter(query.dueFrom()));
		parts.add(dueOnOrBefore(query.dueTo()));
		if (query.overdue()) {
			parts.add(overdueAsOf(today));
		}
		if (query.hasDueDate() != null) {
			parts.add(hasDueDate(query.hasDueDate()));
		}
		parts.removeIf(Objects::isNull);
		return Specification.allOf(parts);
	}

	// ----------------------------------------------------------------- helpers

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static boolean isEmpty(Collection<?> values) {
		return values == null || values.isEmpty();
	}
}
