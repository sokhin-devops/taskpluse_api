package com.taskpulse.api.user;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A registered TaskPulse account. Every {@code Task} and {@code Tag} belongs to exactly
 * one of these, so all data access is scoped by owner.
 *
 * <p>The table is called {@code app_users} rather than {@code users}: the bare name is a
 * reserved or system identifier on several databases, and quoting it everywhere is worse
 * than picking a name that never needs quoting.</p>
 */
@Entity
@Table(name = "app_users", uniqueConstraints = @UniqueConstraint(name = "uk_app_users_email", columnNames = "email"))
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false, updatable = false)
	private Long id;

	/** Login identifier. Always stored lower-cased so lookups are case-insensitive. */
	@Column(name = "email", length = 190, nullable = false)
	private String email;

	@Column(name = "display_name", length = 100, nullable = false)
	private String displayName;

	/** BCrypt hash. Never exposed through any DTO. */
	@Column(name = "password_hash", length = 100, nullable = false)
	private String passwordHash;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public User() {
	}

	public User(String email, String displayName, String passwordHash) {
		this.email = email;
		this.displayName = displayName;
		this.passwordHash = passwordHash;
	}

	@PrePersist
	protected void onCreate() {
		this.createdAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}
