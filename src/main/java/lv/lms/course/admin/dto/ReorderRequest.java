package lv.lms.course.admin.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Ids of all sections (or all lessons of a section) in their new order. */
public record ReorderRequest(@NotNull @Size(max = 500) List<@NotNull Long> ids) {
}
