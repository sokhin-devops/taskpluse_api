package com.taskpulse.api.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Turns a valid {@code Authorization: Bearer <jwt>} header into an authenticated
 * security context for the rest of the request.
 *
 * <p>A missing or unusable token is never an error here: the filter simply leaves the
 * context anonymous and lets the authorization rules decide, so public endpoints keep
 * working and protected ones answer 401 through the configured entry point.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;
	private final AppUserDetailsService userDetailsService;

	public JwtAuthenticationFilter(JwtService jwtService, AppUserDetailsService userDetailsService) {
		this.jwtService = jwtService;
		this.userDetailsService = userDetailsService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {

		String token = bearerToken(request);
		if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
			authenticate(request, token);
		}
		chain.doFilter(request, response);
	}

	private void authenticate(HttpServletRequest request, String token) {
		Long userId = jwtService.extractUserId(token);
		if (userId == null) {
			return;
		}
		try {
			AuthenticatedUser principal = userDetailsService.loadUserById(userId);
			UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
					principal, null, principal.getAuthorities());
			authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
			SecurityContextHolder.getContext().setAuthentication(authentication);
		}
		catch (UsernameNotFoundException ex) {
			// Token signed for an account that has since been removed: stay anonymous.
			logger.debug("Bearer token references a missing account: " + userId);
		}
	}

	private static String bearerToken(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			return null;
		}
		String token = header.substring(BEARER_PREFIX.length()).trim();
		return token.isEmpty() ? null : token;
	}
}
