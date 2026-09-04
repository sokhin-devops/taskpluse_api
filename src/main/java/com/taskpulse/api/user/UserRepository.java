package com.taskpulse.api.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Data access for {@link User}.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

	/** Emails are persisted lower-cased, so callers must normalise before querying. */
	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);
}
