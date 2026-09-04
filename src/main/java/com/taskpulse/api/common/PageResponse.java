package com.taskpulse.api.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A page of results in the shape the TaskPulse clients consume.
 *
 * <p>Spring Data's own {@code Page} serialises with a large, unstable structure (its
 * {@code pageable} and {@code sort} sub-objects change between versions and Boot warns
 * about relying on it). Mapping onto this record keeps the wire contract small, explicit
 * and ours.</p>
 *
 * @param content       the rows on this page
 * @param page          zero-based index of this page
 * @param size          requested page size
 * @param totalElements how many rows match the query in total
 * @param totalPages    how many pages that works out to
 * @param first         whether this is the first page
 * @param last          whether this is the last page
 */
@Schema(name = "PageResponse", description = "One page of results plus the counts needed to page through them.")
public record PageResponse<T>(
		@Schema(description = "The rows on this page.") List<T> content,
		@Schema(description = "Zero-based index of this page.", example = "0") int page,
		@Schema(description = "Requested page size.", example = "10") int size,
		@Schema(description = "Total rows matching the query.", example = "42") long totalElements,
		@Schema(description = "Total number of pages.", example = "5") int totalPages,
		@Schema(description = "Whether this is the first page.", example = "true") boolean first,
		@Schema(description = "Whether this is the last page.", example = "false") boolean last) {

	/**
	 * Converts a Spring Data page, mapping each row through {@code mapper}.
	 *
	 * @param page   the page returned by the repository
	 * @param mapper entity to DTO conversion
	 * @return the same page expressed as DTOs
	 */
	public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
		return new PageResponse<>(
				page.getContent().stream().map(mapper).toList(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages(),
				page.isFirst(),
				page.isLast());
	}
}
