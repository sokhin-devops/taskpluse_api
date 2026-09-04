package com.taskpulse.api.tag;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Data access for {@link Tag}. Every method is scoped by owner id so a caller can never
 * reach another account's tags.
 */
@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

	List<Tag> findAllByOwnerIdOrderByNameAsc(Long ownerId);

	Optional<Tag> findByIdAndOwnerId(Long id, Long ownerId);

	List<Tag> findAllByIdInAndOwnerId(List<Long> ids, Long ownerId);

	boolean existsByOwnerIdAndNameIgnoreCase(Long ownerId, String name);

	/** Duplicate-name check for an update, which must not collide with the row being edited. */
	boolean existsByOwnerIdAndNameIgnoreCaseAndIdNot(Long ownerId, String name, Long id);

	long countByOwnerId(Long ownerId);
}
