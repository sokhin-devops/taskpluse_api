package com.taskpulse.api.task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.taskpulse.api.user.User;

/**
 * Data access for {@link Task}.
 *
 * <p>Every finder is scoped by owner id. {@link JpaSpecificationExecutor} supplies the
 * dynamic list query built in {@link TaskSpecifications}; the aggregate queries below back
 * the dashboard, and are plain JPQL so the same suite runs on H2 in tests and on Postgres
 * at run time.</p>
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

	// ------------------------------------------------------------------ lookups

	Optional<Task> findByIdAndOwnerId(Long id, Long ownerId);

	/**
	 * Board ordering: columns in workflow order, then within a column by explicit position,
	 * then newest first as a tie-break for rows that have never been dragged.
	 *
	 * <p>Ordered on {@code statusWeight} rather than {@code status}: the latter is stored as
	 * a string and would sort DONE, IN_PROGRESS, TODO.</p>
	 *
	 * @return every task of the owner in board order
	 */
	List<Task> findAllByOwnerIdOrderByStatusWeightAscPositionAscIdDesc(Long ownerId);

	/** The tasks of one board column, in the order they should be rendered. */
	List<Task> findAllByOwnerIdAndStatusOrderByPositionAscIdDesc(Long ownerId, TaskStatus status);

	/** Backs tag deletion: a tag cannot be removed while join rows still reference it. */
	@Query("select t from Task t join t.tags tg where tg.id = :tagId")
	List<Task> findAllByTagId(@Param("tagId") Long tagId);

	// --------------------------------------------------------------- dashboard

	long countByOwnerIdAndStatus(Long ownerId, TaskStatus status);

	long countByOwnerId(Long ownerId);

	/** Pending tasks whose due date has already passed. */
	long countByOwnerIdAndCompletedFalseAndDueDateBefore(Long ownerId, LocalDate date);

	long countByOwnerIdAndCompletedFalseAndDueDate(Long ownerId, LocalDate date);

	/** Pending tasks due inside a window, used for the "next 7 days" tile. */
	long countByOwnerIdAndCompletedFalseAndDueDateBetween(Long ownerId, LocalDate from, LocalDate to);

	long countByOwnerIdAndCompletedFalseAndDueDateIsNull(Long ownerId);

	@Query("select t.status as status, count(t) as total from Task t "
			+ "where t.owner.id = :ownerId group by t.status")
	List<StatusCount> countGroupedByStatus(@Param("ownerId") Long ownerId);

	@Query("select t.priority as priority, count(t) as total from Task t "
			+ "where t.owner.id = :ownerId and t.completed = false group by t.priority")
	List<PriorityCount> countOpenGroupedByPriority(@Param("ownerId") Long ownerId);

	/**
	 * Raw creation timestamps for the trend chart. Bucketing by day happens in the service
	 * rather than in SQL: date truncation is spelled differently on every database, and the
	 * row count here is bounded by the requested window.
	 */
	@Query("select t.createdAt from Task t where t.owner.id = :ownerId and t.createdAt >= :since")
	List<LocalDateTime> findCreatedAtSince(@Param("ownerId") Long ownerId, @Param("since") LocalDateTime since);

	@Query("select t.completedAt from Task t "
			+ "where t.owner.id = :ownerId and t.completedAt is not null and t.completedAt >= :since")
	List<LocalDateTime> findCompletedAtSince(@Param("ownerId") Long ownerId, @Param("since") LocalDateTime since);

	@Query("select tg.id as tagId, count(t.id) as taskCount from Task t join t.tags tg "
			+ "where tg.owner.id = :ownerId group by tg.id")
	List<TagUsage> countTasksPerTag(@Param("ownerId") Long ownerId);

	// ------------------------------------------------------------- maintenance

	/**
	 * Repairs rows whose {@code status} and {@code completed} columns disagree.
	 *
	 * <p>Needed once, for tasks created before the kanban workflow existed: the schema
	 * update gives them the {@code TODO} default even when {@code completed} was already
	 * true. The condition is self-limiting, so running it on every start is harmless.</p>
	 *
	 * @return how many rows were repaired
	 */
	@Modifying
	@Query("update Task t set t.status = com.taskpulse.api.task.TaskStatus.DONE, "
			+ "t.statusWeight = 3, t.completedAt = coalesce(t.completedAt, t.updatedAt) "
			+ "where t.completed = true and t.status <> com.taskpulse.api.task.TaskStatus.DONE")
	int markCompletedRowsAsDone();

	/** Counterpart of {@link #markCompletedRowsAsDone()} for the opposite drift. */
	@Modifying
	@Query("update Task t set t.completed = true where t.completed = false "
			+ "and t.status = com.taskpulse.api.task.TaskStatus.DONE")
	int markDoneRowsAsCompleted();

	/**
	 * Brings the derived sort-key columns in line with the enum column they mirror.
	 *
	 * <p>Rows that predate these columns took the SQL default — {@code TODO}'s weight and
	 * {@code MEDIUM}'s — regardless of what their status and priority actually say. Called
	 * once per enum constant at start-up; the {@code <>} guard makes each call a no-op
	 * after the first run.</p>
	 *
	 * @return how many rows were repaired
	 */
	@Modifying
	@Query("update Task t set t.statusWeight = :weight where t.status = :status and t.statusWeight <> :weight")
	int syncStatusWeight(@Param("status") TaskStatus status, @Param("weight") int weight);

	/** Priority counterpart of {@link #syncStatusWeight}. */
	@Modifying
	@Query("update Task t set t.priorityWeight = :weight "
			+ "where t.priority = :priority and t.priorityWeight <> :weight")
	int syncPriorityWeight(@Param("priority") TaskPriority priority, @Param("weight") int weight);

	/**
	 * Adopts tasks that predate accounts, so existing data is not orphaned by the upgrade.
	 *
	 * <p>Takes the owner as an entity, not an id: JPQL cannot assign through an association
	 * path, so {@code set t.owner.id = :id} is not valid and {@code set t.owner = :owner} is.</p>
	 */
	@Modifying
	@Query("update Task t set t.owner = :owner where t.owner is null")
	int assignMissingOwner(@Param("owner") User owner);

	long countByOwnerIsNull();

	// ------------------------------------------------------------- projections

	/**
	 * A {@code group by t.status} row. Declared with the concrete enum type rather than a
	 * shared {@code Enum<?>} projection, so Spring Data can convert the tuple without a
	 * custom converter.
	 */
	interface StatusCount {

		TaskStatus getStatus();

		long getTotal();
	}

	/** A {@code group by t.priority} row. */
	interface PriorityCount {

		TaskPriority getPriority();

		long getTotal();
	}

	/** How many tasks currently carry a given tag. */
	interface TagUsage {

		Long getTagId();

		long getTaskCount();
	}
}
