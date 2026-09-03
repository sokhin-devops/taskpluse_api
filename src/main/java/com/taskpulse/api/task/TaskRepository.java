package com.taskpulse.api.task;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Data access for {@link Task}.
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

	/**
	 * Board ordering: outstanding tasks first, then the soonest due date, then newest first.
	 * Tasks without a due date sort last within their group (Postgres orders NULLs last on ASC).
	 *
	 * @return every task in display order
	 */
	List<Task> findAllByOrderByCompletedAscDueDateAscIdDesc();

}
