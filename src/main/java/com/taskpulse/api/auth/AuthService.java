package com.taskpulse.api.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taskpulse.api.auth.dto.AuthResponse;
import com.taskpulse.api.auth.dto.LoginRequest;
import com.taskpulse.api.auth.dto.RegisterRequest;
import com.taskpulse.api.auth.dto.UserResponse;
import com.taskpulse.api.exception.EmailAlreadyUsedException;
import com.taskpulse.api.exception.InvalidCredentialsException;
import com.taskpulse.api.security.AppUserDetailsService;
import com.taskpulse.api.security.CurrentUser;
import com.taskpulse.api.security.JwtService;
import com.taskpulse.api.tag.TagService;
import com.taskpulse.api.user.User;
import com.taskpulse.api.user.UserRepository;

/**
 * Registration and sign-in.
 *
 * <p>This is the only place in the application that looks at a password. Every other
 * request is authenticated from its bearer token, which is why there is no
 * {@code AuthenticationManager} in the security configuration: the single credential check
 * needed is the {@link PasswordEncoder#matches} call below.</p>
 */
@Service
@Transactional
public class AuthService {

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final TagService tagService;
	private final CurrentUser currentUser;

	public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
			TagService tagService, CurrentUser currentUser) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.tagService = tagService;
		this.currentUser = currentUser;
	}

	/**
	 * Creates an account and signs it straight in.
	 *
	 * @param request the submitted details
	 * @return a token for the new account
	 * @throws EmailAlreadyUsedException when the email is already registered
	 */
	public AuthResponse register(RegisterRequest request) {
		String email = AppUserDetailsService.normalise(request.email());
		if (users.existsByEmail(email)) {
			throw new EmailAlreadyUsedException(email);
		}

		User user = new User(email, request.displayName().trim(),
				passwordEncoder.encode(request.password()));
		User saved = users.save(user);

		// A new account with no tags makes the tag picker look broken rather than empty.
		tagService.createStarterTags(saved);

		return tokenFor(saved);
	}

	/**
	 * Verifies credentials and issues a token.
	 *
	 * @param request the submitted credentials
	 * @return a token for the account
	 * @throws InvalidCredentialsException when the email is unknown or the password wrong.
	 *         The same exception covers both cases on purpose: telling them apart would let
	 *         a caller discover which addresses are registered.
	 */
	public AuthResponse login(LoginRequest request) {
		String email = AppUserDetailsService.normalise(request.email());
		User user = users.findByEmail(email)
				.orElseThrow(InvalidCredentialsException::new);

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}
		return tokenFor(user);
	}

	/**
	 * @return the account behind the bearer token on the current request
	 */
	@Transactional(readOnly = true)
	public UserResponse currentAccount() {
		return UserResponse.from(currentUser.requireEntity());
	}

	private AuthResponse tokenFor(User user) {
		return AuthResponse.of(jwtService.issue(user), jwtService.getExpiresInSeconds(),
				UserResponse.from(user));
	}
}
