package com.taskpulse.api.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.taskpulse.api.user.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and verifies the HS256 bearer tokens used by the API.
 *
 * <p>The subject is the user id (stable, unlike an email that can be changed) and the
 * email/display name ride along as claims so the client can render the signed-in user
 * without an extra round trip.</p>
 */
@Service
public class JwtService {

	/** HS256 needs at least 256 bits of key material; a shorter secret is rejected outright. */
	private static final int MIN_SECRET_BYTES = 32;

	private static final Logger log = LoggerFactory.getLogger(JwtService.class);

	static final String CLAIM_EMAIL = "email";
	static final String CLAIM_NAME = "name";

	private final SecretKey key;
	private final Duration ttl;

	public JwtService(@Value("${app.jwt.secret}") String secret,
			@Value("${app.jwt.expiration-minutes:720}") long expirationMinutes) {
		byte[] material = secret.getBytes(StandardCharsets.UTF_8);
		if (material.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("app.jwt.secret must be at least " + MIN_SECRET_BYTES
					+ " characters; got " + material.length);
		}
		this.key = Keys.hmacShaKeyFor(material);
		this.ttl = Duration.ofMinutes(expirationMinutes);
	}

	/**
	 * @return a signed token identifying the given user
	 */
	public String issue(User user) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(String.valueOf(user.getId()))
				.claim(CLAIM_EMAIL, user.getEmail())
				.claim(CLAIM_NAME, user.getDisplayName())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(ttl)))
				.signWith(key)
				.compact();
	}

	/**
	 * Verifies the signature and expiry of a token.
	 *
	 * @param token the raw compact JWT
	 * @return the user id carried by the token, or {@code null} when it is invalid,
	 *         expired or malformed
	 */
	public Long extractUserId(String token) {
		try {
			Claims claims = Jwts.parser()
					.verifyWith(key)
					.build()
					.parseSignedClaims(token)
					.getPayload();
			return Long.valueOf(claims.getSubject());
		}
		catch (JwtException | IllegalArgumentException ex) {
			// An unusable token is an anonymous request, not a server fault: log and move on.
			log.debug("Rejected bearer token: {}", ex.getMessage());
			return null;
		}
	}

	/**
	 * @return how long an issued token stays valid, in seconds
	 */
	public long getExpiresInSeconds() {
		return ttl.toSeconds();
	}
}
