package com.taskpulse.api.tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taskpulse.api.exception.TagNotFoundException;
import com.taskpulse.api.security.CurrentUser;
import com.taskpulse.api.tag.dto.TagRequest;
import com.taskpulse.api.tag.dto.TagResponse;
import com.taskpulse.api.task.Task;
import com.taskpulse.api.task.TaskRepository;
import com.taskpulse.api.user.User;

/**
 * Application service for tag management.
 *
 * <p>Tags are per-account, so every method resolves the caller through
 * {@link CurrentUser} and scopes its queries by owner id. A tag belonging to somebody else
 * is reported as not found rather than forbidden, for the reason described on
 * {@link com.taskpulse.api.exception.TaskNotFoundException}.</p>
 */
@Service
@Transactional
public class TagService {

	/**
	 * Labels every new account starts with, so the tag picker is never empty on day one.
	 *
	 * <p>These four colours are not an arbitrary pick from the palette: they are the
	 * combination that stays mutually distinguishable under every simulated form of colour
	 * blindness, checked across all six pairs rather than only neighbouring ones. A new
	 * account therefore cannot start out with two tags that look the same to its owner.</p>
	 */
	private static final List<String[]> STARTER_TAGS = List.of(
			new String[] { "Work", "#2a78d6" },
			new String[] { "Personal", "#1baf7a" },
			new String[] { "Urgent", "#eb6834" },
			new String[] { "Ideas", "#4a3aa7" });

	private final TagRepository tags;
	private final TaskRepository tasks;
	private final CurrentUser currentUser;

	public TagService(TagRepository tags, TaskRepository tasks, CurrentUser currentUser) {
		this.tags = tags;
		this.tasks = tasks;
		this.currentUser = currentUser;
	}

	// -------------------------------------------------------------------- read

	/**
	 * @return the caller's tags in alphabetical order, each with its usage count
	 */
	@Transactional(readOnly = true)
	public List<TagResponse> findAll() {
		Long ownerId = currentUser.requireId();
		Map<Long, Long> usage = usageByTagId(ownerId);
		return tags.findAllByOwnerIdOrderByNameAsc(ownerId).stream()
				.map(tag -> TagResponse.withCount(tag, usage.getOrDefault(tag.getId(), 0L)))
				.toList();
	}

	/**
	 * The caller's most-used tags, for the dashboard.
	 *
	 * @param limit how many to return
	 * @return tags ordered by usage, busiest first; unused tags are omitted
	 */
	@Transactional(readOnly = true)
	public List<TagResponse> findTopUsed(int limit) {
		Long ownerId = currentUser.requireId();
		Map<Long, Long> usage = usageByTagId(ownerId);
		return tags.findAllByOwnerIdOrderByNameAsc(ownerId).stream()
				.filter(tag -> usage.containsKey(tag.getId()))
				.map(tag -> TagResponse.withCount(tag, usage.get(tag.getId())))
				.sorted((left, right) -> Long.compare(right.taskCount(), left.taskCount()))
				.limit(limit)
				.toList();
	}

	// ------------------------------------------------------------------ write

	public TagResponse create(TagRequest request) {
		User owner = currentUser.requireEntity();
		String name = request.name().trim();
		if (tags.existsByOwnerIdAndNameIgnoreCase(owner.getId(), name)) {
			throw new IllegalArgumentException("You already have a tag called '" + name + "'");
		}
		Tag tag = new Tag(name, colorOrDefault(request.color()), owner);
		return TagResponse.withCount(tags.save(tag), 0L);
	}

	public TagResponse update(Long id, TagRequest request) {
		Long ownerId = currentUser.requireId();
		Tag tag = getOrThrow(id, ownerId);
		String name = request.name().trim();
		if (tags.existsByOwnerIdAndNameIgnoreCaseAndIdNot(ownerId, name, id)) {
			throw new IllegalArgumentException("You already have a tag called '" + name + "'");
		}
		tag.setName(name);
		tag.setColor(colorOrDefault(request.color()));
		Tag saved = tags.save(tag);
		return TagResponse.withCount(saved, usageByTagId(ownerId).getOrDefault(id, 0L));
	}

	/**
	 * Deletes a tag, first detaching it from every task that carries it.
	 *
	 * <p>The detach step is not optional: the tag is referenced by rows in the
	 * {@code task_tags} join table, and deleting it while those exist violates the foreign
	 * key. Clearing the association through the owning side lets Hibernate remove the join
	 * rows for us, which keeps this portable instead of hand-writing a delete against a
	 * table no entity maps.</p>
	 */
	public void delete(Long id) {
		Long ownerId = currentUser.requireId();
		Tag tag = getOrThrow(id, ownerId);

		List<Task> tagged = tasks.findAllByTagId(id);
		tagged.forEach(task -> task.removeTag(tag));
		tasks.saveAll(tagged);

		tags.delete(tag);
	}

	/**
	 * Gives a brand-new account its starter tags.
	 *
	 * @param owner the account being created
	 */
	public void createStarterTags(User owner) {
		List<Tag> starters = STARTER_TAGS.stream()
				.map(entry -> new Tag(entry[0], entry[1], owner))
				.toList();
		tags.saveAll(starters);
	}

	// --------------------------------------------------------------- internal

	/**
	 * Resolves tag ids supplied on a task write.
	 *
	 * <p>Every id must belong to the caller. Missing ones are reported together rather than
	 * one per round trip, and an id owned by another account is indistinguishable from one
	 * that does not exist — which is the point.</p>
	 *
	 * @param ids     the requested tag ids; may be {@code null} or empty
	 * @param ownerId the account the tags must belong to
	 * @return the matching tags
	 * @throws TagNotFoundException when any id does not resolve
	 */
	@Transactional(readOnly = true)
	public Set<Tag> resolveForOwner(List<Long> ids, Long ownerId) {
		if (ids == null || ids.isEmpty()) {
			return Set.of();
		}
		List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
		if (distinct.isEmpty()) {
			return Set.of();
		}
		List<Tag> found = tags.findAllByIdInAndOwnerId(distinct, ownerId);
		if (found.size() != distinct.size()) {
			List<Long> missing = new ArrayList<>(distinct);
			found.forEach(tag -> missing.remove(tag.getId()));
			throw new TagNotFoundException(missing);
		}
		return new LinkedHashSet<>(found);
	}

	private Tag getOrThrow(Long id, Long ownerId) {
		return tags.findByIdAndOwnerId(id, ownerId)
				.orElseThrow(() -> new TagNotFoundException(id));
	}

	private Map<Long, Long> usageByTagId(Long ownerId) {
		Map<Long, Long> usage = new HashMap<>();
		for (TaskRepository.TagUsage row : tasks.countTasksPerTag(ownerId)) {
			usage.put(row.getTagId(), row.getTaskCount());
		}
		return usage;
	}

	private static String colorOrDefault(String color) {
		return (color == null || color.isBlank()) ? Tag.DEFAULT_COLOR : color.trim().toLowerCase();
	}
}
