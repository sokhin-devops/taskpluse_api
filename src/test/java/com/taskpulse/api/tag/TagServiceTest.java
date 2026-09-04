package com.taskpulse.api.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.taskpulse.api.exception.TagNotFoundException;
import com.taskpulse.api.security.CurrentUser;
import com.taskpulse.api.tag.dto.TagRequest;
import com.taskpulse.api.tag.dto.TagResponse;
import com.taskpulse.api.task.Task;
import com.taskpulse.api.task.TaskRepository;
import com.taskpulse.api.user.User;

/**
 * Unit tests for {@link TagService}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TagServiceTest {

	private static final Long OWNER_ID = 7L;

	@Mock
	private TagRepository tags;

	@Mock
	private TaskRepository tasks;

	@Mock
	private CurrentUser currentUser;

	private TagService service;

	private User owner;

	@BeforeEach
	void setUp() {
		owner = new User("sam@example.com", "Sam Rivera", "hash");
		owner.setId(OWNER_ID);

		service = new TagService(tags, tasks, currentUser);

		when(currentUser.requireId()).thenReturn(OWNER_ID);
		when(currentUser.requireEntity()).thenReturn(owner);
		when(tags.save(any(Tag.class))).thenAnswer(call -> call.getArgument(0));
		when(tasks.countTasksPerTag(OWNER_ID)).thenReturn(List.of());
	}

	/**
	 * Builds a tag without touching the mocks.
	 *
	 * <p>Deliberately free of stubbing: calling {@code when(...)} while evaluating an
	 * argument to another {@code when(...)} is what Mockito reports as UnfinishedStubbing.
	 * Tests that need a lookup to resolve call {@link #stubLookup(Tag)} afterwards.</p>
	 */
	private Tag tag(Long id, String name) {
		Tag tag = new Tag(name, "#6366f1", owner);
		tag.setId(id);
		return tag;
	}

	/** Makes {@code findByIdAndOwnerId} resolve to the given tag for the test's owner. */
	private Tag stubLookup(Tag tag) {
		when(tags.findByIdAndOwnerId(tag.getId(), OWNER_ID)).thenReturn(Optional.of(tag));
		return tag;
	}

	private static TaskRepository.TagUsage usage(Long tagId, long count) {
		return new TaskRepository.TagUsage() {
			@Override
			public Long getTagId() {
				return tagId;
			}

			@Override
			public long getTaskCount() {
				return count;
			}
		};
	}

	// -------------------------------------------------------------------- read

	@Test
	@DisplayName("reports a usage count of zero for a tag no task carries")
	void unusedTagCountsZero() {
		when(tags.findAllByOwnerIdOrderByNameAsc(OWNER_ID)).thenReturn(List.of(tag(1L, "Work")));

		assertThat(service.findAll()).singleElement()
				.satisfies(response -> assertThat(response.taskCount()).isZero());
	}

	@Test
	@DisplayName("attaches the usage count reported by the task repository")
	void reportsUsageCounts() {
		when(tags.findAllByOwnerIdOrderByNameAsc(OWNER_ID))
				.thenReturn(List.of(tag(1L, "Work"), tag(2L, "Home")));
		when(tasks.countTasksPerTag(OWNER_ID)).thenReturn(List.of(usage(1L, 5)));

		assertThat(service.findAll())
				.extracting(TagResponse::name, TagResponse::taskCount)
				.containsExactly(tuple("Work", 5L), tuple("Home", 0L));
	}

	@Test
	@DisplayName("ranks the busiest tags first and leaves out unused ones")
	void topUsedIsRankedAndFiltered() {
		when(tags.findAllByOwnerIdOrderByNameAsc(OWNER_ID))
				.thenReturn(List.of(tag(1L, "Admin"), tag(2L, "Home"), tag(3L, "Work")));
		when(tasks.countTasksPerTag(OWNER_ID)).thenReturn(List.of(usage(1L, 2), usage(3L, 9)));

		assertThat(service.findTopUsed(5))
				.extracting(TagResponse::name)
				.containsExactly("Work", "Admin");
	}

	@Test
	@DisplayName("honours the limit on the busiest-tags list")
	void topUsedRespectsTheLimit() {
		when(tags.findAllByOwnerIdOrderByNameAsc(OWNER_ID))
				.thenReturn(List.of(tag(1L, "A"), tag(2L, "B"), tag(3L, "C")));
		when(tasks.countTasksPerTag(OWNER_ID))
				.thenReturn(List.of(usage(1L, 1), usage(2L, 2), usage(3L, 3)));

		assertThat(service.findTopUsed(2)).extracting(TagResponse::name).containsExactly("C", "B");
	}

	// ------------------------------------------------------------------- write

	@Test
	@DisplayName("trims the name and applies the default colour when none is given")
	void normalisesOnCreate() {
		TagResponse created = service.create(new TagRequest("  Work  ", null));

		assertThat(created.name()).isEqualTo("Work");
		assertThat(created.color()).isEqualTo(Tag.DEFAULT_COLOR);
		assertThat(created.taskCount()).isZero();
	}

	@Test
	@DisplayName("lower-cases a supplied colour so the stored value is predictable")
	void normalisesColour() {
		assertThat(service.create(new TagRequest("Work", "#AABBCC")).color()).isEqualTo("#aabbcc");
	}

	@Test
	@DisplayName("assigns the signed-in account as the owner")
	void createSetsOwner() {
		service.create(new TagRequest("Work", null));

		ArgumentCaptor<Tag> saved = ArgumentCaptor.forClass(Tag.class);
		verify(tags).save(saved.capture());
		assertThat(saved.getValue().getOwner()).isSameAs(owner);
	}

	@Test
	@DisplayName("refuses a duplicate name within the same account")
	void rejectsDuplicateName() {
		when(tags.existsByOwnerIdAndNameIgnoreCase(OWNER_ID, "Work")).thenReturn(true);

		assertThatThrownBy(() -> service.create(new TagRequest("Work", null)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Work");
	}

	@Test
	@DisplayName("lets a tag keep its own name when only the colour changes")
	void updateDoesNotCollideWithItself() {
		stubLookup(tag(1L, "Work"));

		TagResponse updated = service.update(1L, new TagRequest("Work", "#ef4444"));

		assertThat(updated.name()).isEqualTo("Work");
		assertThat(updated.color()).isEqualTo("#ef4444");
	}

	@Test
	@DisplayName("refuses to rename a tag onto another tag's name")
	void updateRejectsDuplicateName() {
		stubLookup(tag(1L, "Work"));
		when(tags.existsByOwnerIdAndNameIgnoreCaseAndIdNot(OWNER_ID, "Home", 1L)).thenReturn(true);

		assertThatThrownBy(() -> service.update(1L, new TagRequest("Home", null)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("takes a tag off every task before deleting it")
	void deleteDetachesFromTasks() {
		Tag work = stubLookup(tag(1L, "Work"));
		Task tagged = new Task();
		tagged.setId(10L);
		tagged.getTags().add(work);
		when(tasks.findAllByTagId(1L)).thenReturn(List.of(tagged));

		service.delete(1L);

		assertThat(tagged.getTags()).isEmpty();
		verify(tasks).saveAll(List.of(tagged));
		verify(tags).delete(work);
	}

	@Test
	@DisplayName("reports a tag from another account as not found and deletes nothing")
	void deleteUnknownTag() {
		when(tags.findByIdAndOwnerId(99L, OWNER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(TagNotFoundException.class);

		verify(tags, never()).delete(any(Tag.class));
	}

	@Test
	@DisplayName("gives a new account its starter tags")
	void createsStarterTags() {
		service.createStarterTags(owner);

		ArgumentCaptor<List<Tag>> saved = ArgumentCaptor.captor();
		verify(tags).saveAll(saved.capture());
		assertThat(saved.getValue()).isNotEmpty()
				.allSatisfy(tag -> assertThat(tag.getOwner()).isSameAs(owner));
	}

	// ---------------------------------------------------------------- resolving

	@Test
	@DisplayName("resolves an empty or null id list to no tags without querying")
	void resolvesEmptyWithoutQuerying() {
		assertThat(service.resolveForOwner(null, OWNER_ID)).isEmpty();
		assertThat(service.resolveForOwner(List.of(), OWNER_ID)).isEmpty();

		verify(tags, never()).findAllByIdInAndOwnerId(any(), any());
	}

	@Test
	@DisplayName("collapses duplicate ids before querying")
	void deduplicatesIds() {
		Tag work = new Tag("Work", "#6366f1", owner);
		work.setId(1L);
		when(tags.findAllByIdInAndOwnerId(List.of(1L), OWNER_ID)).thenReturn(List.of(work));

		assertThat(service.resolveForOwner(List.of(1L, 1L, 1L), OWNER_ID)).containsExactly(work);
	}

	@Test
	@DisplayName("names every id that did not resolve, rather than only the first")
	void reportsAllMissingIds() {
		Tag work = new Tag("Work", "#6366f1", owner);
		work.setId(1L);
		when(tags.findAllByIdInAndOwnerId(List.of(1L, 2L, 3L), OWNER_ID)).thenReturn(List.of(work));

		assertThatThrownBy(() -> service.resolveForOwner(List.of(1L, 2L, 3L), OWNER_ID))
				.isInstanceOf(TagNotFoundException.class)
				.hasMessageContaining("2")
				.hasMessageContaining("3");
	}

	@Test
	@DisplayName("scopes the resolution query to the caller's account")
	void resolveIsOwnerScoped() {
		when(tags.findAllByIdInAndOwnerId(List.of(4L), OWNER_ID)).thenReturn(List.of());

		assertThatThrownBy(() -> service.resolveForOwner(List.of(4L), OWNER_ID))
				.isInstanceOf(TagNotFoundException.class);

		verify(tags).findAllByIdInAndOwnerId(List.of(4L), OWNER_ID);
	}

	@Test
	@DisplayName("ignores nulls inside the supplied id list")
	void ignoresNullIds() {
		assertThat(service.resolveForOwner(Arrays.asList(null, null), OWNER_ID)).isEmpty();
	}

	@Test
	@DisplayName("uses the caller's tags only, never a shared namespace")
	void tagsAreScopedPerAccount() {
		when(tags.findAllByOwnerIdOrderByNameAsc(OWNER_ID)).thenReturn(List.of());

		assertThat(service.findAll()).isEmpty();

		verify(tags).findAllByOwnerIdOrderByNameAsc(OWNER_ID);
		verify(tags, never()).findAll();
	}

	@Test
	@DisplayName("keeps the resolved set in the order the ids were given")
	void resolvePreservesOrder() {
		Tag first = new Tag("Alpha", "#111111", owner);
		first.setId(1L);
		Tag second = new Tag("Beta", "#222222", owner);
		second.setId(2L);
		when(tags.findAllByIdInAndOwnerId(List.of(1L, 2L), OWNER_ID)).thenReturn(List.of(first, second));

		Set<Tag> resolved = service.resolveForOwner(List.of(1L, 2L), OWNER_ID);

		assertThat(resolved).containsExactly(first, second);
	}
}
