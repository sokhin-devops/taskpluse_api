package com.taskpulse.api.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.taskpulse.api.user.User;
import com.taskpulse.api.user.UserRepository;

/**
 * Reads the caller out of the security context.
 *
 * <p>Every task and tag query is scoped by owner, so the service layer asks this
 * component for the id instead of threading a parameter down from the controllers.</p>
 */
@Component
public class CurrentUser {

	private final UserRepository users;

	public CurrentUser(UserRepository users) {
		this.users = users;
	}

	/**
	 * @return the authenticated principal
	 * @throws IllegalStateException when called outside an authenticated request, which
	 *         would be a wiring bug: the security rules must reject those before this point
	 */
	public AuthenticatedUser require() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser principal) {
			return principal;
		}
		throw new IllegalStateException("No authenticated user in the security context");
	}

	public Long requireId() {
		return require().getId();
	}

	/**
	 * @return the caller as a managed entity, for use as the owner of something new
	 */
	public User requireEntity() {
		Long id = requireId();
		return users.findById(id)
				.orElseThrow(() -> new IllegalStateException("Authenticated user " + id + " no longer exists"));
	}
}
