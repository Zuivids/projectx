package lv.lms.course.catalog;

import java.util.List;

import lv.lms.course.CourseLevel;
import lv.lms.course.LessonType;

/**
 * Public catalog DTOs. They carry no internal ids of courses, no status and no lesson
 * content except for free previews.
 */
public final class CatalogResponses {

	private CatalogResponses() {
	}

	public record CourseSummary(String slug, String title, String description, long priceCents, String currency,
			CourseLevel level, int lessonCount, int totalDurationSeconds) {
	}

	public record CourseDetail(String slug, String title, String description, long priceCents, String currency,
			CourseLevel level, int lessonCount, int totalDurationSeconds, List<Section> sections) {
	}

	public record Section(String title, List<LessonOutline> lessons) {
	}

	/** Table-of-contents entry; {@code id} is used to open a preview lesson. */
	public record LessonOutline(long id, String title, LessonType type, Integer durationSeconds, boolean preview) {
	}

	public record PreviewLesson(long id, String title, LessonType type, String contentText, String audioRef,
			Integer durationSeconds) {
	}

}
