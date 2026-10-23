package lv.lms.course.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lv.lms.course.CourseLevel;
import lv.lms.course.Slugs;

/**
 * @param slug optional; generated from the title when empty
 * @param priceCents price in euro cents, e.g. 4900 for 49.00 EUR
 */
public record CourseCreateRequest(
		@NotBlank @Size(max = 200) String title,
		@Size(max = Slugs.MAX_LENGTH) @Pattern(regexp = Slugs.PATTERN) String slug,
		@NotNull @Size(max = 20_000) String description,
		@NotNull @Min(0) @Max(100_000_00) Long priceCents,
		CourseLevel level) {
}
