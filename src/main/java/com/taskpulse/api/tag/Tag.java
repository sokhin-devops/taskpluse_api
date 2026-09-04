package com.taskpulse.api.tag;

import java.time.LocalDateTime;
import java.util.Objects;

import com.taskpulse.api.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A reusable, colour-coded label a task can carry. Tags are per-account: two users may
 * both have a tag called "Work" without collision, which the unique constraint below
 * expresses as (owner, name) rather than name alone.
 */
@Entity
@Table(name = "tags",
		uniqueConstraints = @UniqueConstraint(name = "uk_tags_owner_name", columnNames = { "owner_id", "name" }))
public class Tag {

	/**
	 * Fallback colour for a tag created without one.
	 *
	 * <p>One of the eight values offered by the web client's tag palette, which were
	 * picked by running a colour-vision validator over the set rather than by eye. Keep
	 * this in step with {@code TAG_COLOR_CHOICES} in {@code tag.model.ts}.</p>
	 */
	public static final String DEFAULT_COLOR = "#2a78d6";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false, updatable = false)
	private Long id;

	@Column(name = "name", length = 40, nullable = false)
	private String name;

	/** Six-digit hex colour including the leading '#', e.g. {@code #6366f1}. */
	@Column(name = "color", length = 7, nullable = false)
	private String color = DEFAULT_COLOR;

	/**
	 * Owning account. Nullable in the database only so that the column can be added to an
	 * existing table without a rewrite; the service layer always sets it.
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id")
	private User owner;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public Tag() {
	}

	public Tag(String name, String color, User owner) {
		this.name = name;
		this.color = color;
		this.owner = owner;
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

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getColor() {
		return color;
	}

	public void setColor(String color) {
		this.color = color;
	}

	public User getOwner() {
		return owner;
	}

	public void setOwner(User owner) {
		this.owner = owner;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	/**
	 * Identity by primary key. Tags live in a {@code Set} on {@link com.taskpulse.api.task.Task},
	 * so removing one from a task depends on equality behaving predictably. Unsaved tags
	 * (id {@code null}) fall back to instance identity, which is the only safe answer.
	 */
	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof Tag tag) || id == null || tag.id == null) {
			return false;
		}
		return id.equals(tag.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
