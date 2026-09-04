package com.taskpulse.api.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.taskpulse.api.user.User;

/**
 * The principal placed in the security context for an authenticated request.
 *
 * <p>Spring's own {@code UserDetails} carries only a username, but every query in this
 * application is scoped by owner id, so the id is carried here too and read back through
 * {@link CurrentUser}. That keeps the service layer free of extra lookups on each call.</p>
 */
public class AuthenticatedUser implements UserDetails {

	private static final long serialVersionUID = 1L;

	private final Long id;
	private final String email;
	private final String displayName;
	private final String passwordHash;

	public AuthenticatedUser(Long id, String email, String displayName, String passwordHash) {
		this.id = id;
		this.email = email;
		this.displayName = displayName;
		this.passwordHash = passwordHash;
	}

	public static AuthenticatedUser from(User user) {
		return new AuthenticatedUser(user.getId(), user.getEmail(), user.getDisplayName(), user.getPasswordHash());
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getDisplayName() {
		return displayName;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		// Single-role application: authorisation is by data ownership, not by role.
		return List.of();
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}
}
