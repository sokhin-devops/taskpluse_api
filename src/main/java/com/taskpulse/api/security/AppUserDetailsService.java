package com.taskpulse.api.security;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taskpulse.api.user.UserRepository;

/**
 * Loads accounts for Spring Security by email.
 *
 * <p>Defining this bean also suppresses Boot's fallback in-memory user with its
 * randomly generated console password.</p>
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

	private final UserRepository users;

	public AppUserDetailsService(UserRepository users) {
		this.users = users;
	}

	@Override
	@Transactional(readOnly = true)
	public AuthenticatedUser loadUserByUsername(String email) throws UsernameNotFoundException {
		return users.findByEmail(normalise(email))
				.map(AuthenticatedUser::from)
				.orElseThrow(() -> new UsernameNotFoundException("No account for " + email));
	}

	@Transactional(readOnly = true)
	public AuthenticatedUser loadUserById(Long id) throws UsernameNotFoundException {
		return users.findById(id)
				.map(AuthenticatedUser::from)
				.orElseThrow(() -> new UsernameNotFoundException("No account with id " + id));
	}

	/** Emails are stored lower-cased, so every lookup normalises first. */
	public static String normalise(String email) {
		return email == null ? null : email.trim().toLowerCase();
	}
}
