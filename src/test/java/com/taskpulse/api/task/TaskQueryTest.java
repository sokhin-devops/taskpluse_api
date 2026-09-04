package com.taskpulse.api.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Unit tests for {@link TaskQuery}, the guard between raw query parameters and the
 * repository.
 *
 * <p>The sort allow-list is the security-relevant part: without it a caller could order by
 * any mapped property, including ones reached through an association.</p>
 */
class TaskQueryTest {

	/** A query with every filter left empty, so a test can vary one thing at a time. */
	private static TaskQuery query(int page, int size, String sort, String direction) {
		return new TaskQuery(null, Set.of(), Set.of(), List.of(), false,
				null, null, false, null, page, size, sort, direction);
	}

	@Test
	@DisplayName("falls back to the default sort when none is given")
	void defaultsTheSort() {
		assertThat(query(0, 10, null, null).sort()).isEqualTo(TaskQuery.DEFAULT_SORT);
		assertThat(query(0, 10, "   ", null).sort()).isEqualTo(TaskQuery.DEFAULT_SORT);
	}

	@Test
	@DisplayName("rejects a sort field that is not on the allow-list")
	void rejectsUnknownSort() {
		assertThatThrownBy(() -> query(0, 10, "owner.email", "asc"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("owner.email")
				.hasMessageContaining("dueDate");
	}

	@Test
	@DisplayName("caps the page size and replaces a nonsensical one")
	void normalisesPageSize() {
		assertThat(query(0, 5_000, null, null).size()).isEqualTo(TaskQuery.MAX_PAGE_SIZE);
		assertThat(query(0, 0, null, null).size()).isEqualTo(TaskQuery.DEFAULT_PAGE_SIZE);
		assertThat(query(0, -3, null, null).size()).isEqualTo(TaskQuery.DEFAULT_PAGE_SIZE);
		assertThat(query(0, 25, null, null).size()).isEqualTo(25);
	}

	@Test
	@DisplayName("rejects a negative page index")
	void rejectsNegativePage() {
		assertThatThrownBy(() -> query(-1, 10, null, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("page");
	}

	@Test
	@DisplayName("treats anything that is not 'desc' as ascending")
	void normalisesDirection() {
		assertThat(query(0, 10, null, "DESC").direction()).isEqualTo("desc");
		assertThat(query(0, 10, null, "desc").direction()).isEqualTo("desc");
		assertThat(query(0, 10, null, "sideways").direction()).isEqualTo("asc");
		assertThat(query(0, 10, null, null).direction()).isEqualTo("asc");
	}

	@Test
	@DisplayName("replaces null collections with empty ones so callers need no null checks")
	void normalisesCollections() {
		TaskQuery result = new TaskQuery("x", null, null, null, false,
				null, null, false, null, 0, 10, null, null);

		assertThat(result.status()).isEmpty();
		assertThat(result.priority()).isEmpty();
		assertThat(result.tagIds()).isEmpty();
	}

	@Test
	@DisplayName("sorts 'priority' by its numeric weight, not its name")
	void mapsPriorityOntoItsWeightColumn() {
		Pageable pageable = query(0, 10, "priority", "desc").toPageable();

		assertThat(pageable.getSort().getOrderFor("priorityWeight")).isNotNull();
		assertThat(pageable.getSort().getOrderFor("priority")).isNull();
	}

	@Test
	@DisplayName("sorts 'status' by its workflow weight, not its name")
	void mapsStatusOntoItsWeightColumn() {
		Pageable pageable = query(0, 10, "status", "asc").toPageable();

		assertThat(pageable.getSort().getOrderFor("statusWeight")).isNotNull();
		assertThat(pageable.getSort().getOrderFor("status")).isNull();
	}

	@Test
	@DisplayName("puts undated tasks last when sorting by due date")
	void sortsUndatedTasksLast() {
		Sort.Order order = query(0, 10, "dueDate", "asc").toPageable().getSort().getOrderFor("dueDate");

		assertThat(order).isNotNull();
		assertThat(order.getNullHandling()).isEqualTo(Sort.NullHandling.NULLS_LAST);
	}

	@Test
	@DisplayName("appends a stable tie-break so paging cannot repeat or skip rows")
	void addsDeterministicTieBreak() {
		Sort sort = query(2, 10, "title", "asc").toPageable().getSort();

		Sort.Order tieBreak = sort.getOrderFor("id");
		assertThat(tieBreak).isNotNull();
		assertThat(tieBreak.getDirection()).isEqualTo(Sort.Direction.DESC);
		assertThat(sort.stream().map(Sort.Order::getProperty)).containsExactly("title", "id");
	}

	@Test
	@DisplayName("passes the page index through to the pageable")
	void buildsThePageRequest() {
		Pageable pageable = query(3, 20, "createdAt", "desc").toPageable();

		assertThat(pageable.getPageNumber()).isEqualTo(3);
		assertThat(pageable.getPageSize()).isEqualTo(20);
		assertThat(pageable.getSort().getOrderFor("createdAt").getDirection())
				.isEqualTo(Sort.Direction.DESC);
	}
}
