package lv.lms.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * One page of results. Used instead of Spring's {@code Page}, whose JSON form is not a
 * stable API.
 *
 * @param page zero-based page number
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

	public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
		return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}

}
