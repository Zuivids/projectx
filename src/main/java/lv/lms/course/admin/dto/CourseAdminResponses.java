package lv.lms.course.admin.dto;

import java.time.Instant;
import java.util.List;

import lv.lms.course.CourseLevel;
import lv.lms.course.CourseStatus;
import lv.lms.course.LessonType;

/** Response DTOs of the admin course API. */
public final class CourseAdminResponses {

	private CourseAdminResponses() {
	}

	public record CourseSummary(long id, String title, String slug, CourseStatus status, long priceCents,
			String currency, CourseLevel level, int sectionCount, int lessonCount, Instant updatedAt) {
	}

	/**
	 * Full course for the editor. Every change in the editor returns this again, so the
	 * screen always shows the saved state.
	 *
	 * @param version send back with the next course update
	 * @param canBeDeleted only drafts that were never published can be deleted
	 */
	public record CourseDetail(long id, String title, String slug, String description, long priceCents,
			String currency, CourseLevel level, CourseStatus status, long version, Instant publishedAt,
			Instant updatedAt, boolean canBeDeleted, List<Section> sections) {
	}

	public record Section(long id, String title, int position, List<Lesson> lessons) {
	}

	public record Lesson(long id, String title, LessonType type, String contentText, String audioRef,
			Integer durationSeconds, boolean preview, int position) {
	}

}
