package lv.lms.course;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lv.lms.common.ApiException;

/** Keeps the positions of sections and lessons as 0, 1, 2, … without gaps. */
final class Positions {

	private Positions() {
	}

	interface Positioned {

		Long getId();

		void setPosition(int position);

	}

	static void renumber(List<? extends Positioned> items) {
		for (int i = 0; i < items.size(); i++) {
			items.get(i).setPosition(i);
		}
	}

	/**
	 * Applies a new order. The list must contain every id exactly once, so a client with an
	 * outdated view cannot silently drop or duplicate items.
	 */
	static <T extends Positioned> void reorder(List<T> items, List<Long> orderedIds) {
		if (orderedIds.size() != items.size() || !new HashSet<>(orderedIds).equals(
				items.stream().map(Positioned::getId).collect(Collectors.toSet()))) {
			throw ApiException.badRequest("The new order must list every item exactly once");
		}
		Map<Long, Integer> index = orderedIds.stream()
			.collect(Collectors.toMap(Function.identity(), orderedIds::indexOf));
		items.sort(Comparator.comparing((T item) -> index.get(item.getId())));
		renumber(items);
	}

}
