package lv.lms.course.catalog;

import java.util.Objects;

import lv.lms.common.ApiException;
import lv.lms.common.PageResponse;
import lv.lms.course.Course;
import lv.lms.course.CourseRepository;
import lv.lms.course.CourseStatus;
import lv.lms.course.Lesson;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only view of published courses for visitors and learners. */
@Service
public class CatalogService {

	private static final int MAX_PAGE_SIZE = 50;

	private final CourseRepository courses;

	CatalogService(CourseRepository courses) {
		this.courses = courses;
	}

	@Transactional(readOnly = true)
	public PageResponse<CatalogResponses.CourseSummary> list(int page, int size) {
		PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
				Sort.by(Sort.Direction.DESC, "publishedAt"));
		return PageResponse.of(this.courses.findByStatus(CourseStatus.PUBLISHED, pageable), (course) ->
				new CatalogResponses.CourseSummary(course.getSlug(), course.getTitle(), course.getDescription(),
						course.getPriceCents(), course.getCurrency(), course.getLevel(), course.lessonCount(),
						totalDuration(course)));
	}

	@Transactional(readOnly = true)
	public CatalogResponses.CourseDetail get(String slug) {
		Course course = findPublished(slug);
		return new CatalogResponses.CourseDetail(course.getSlug(), course.getTitle(), course.getDescription(),
				course.getPriceCents(), course.getCurrency(), course.getLevel(), course.lessonCount(),
				totalDuration(course),
				course.getSections()
					.stream()
					.map((s) -> new CatalogResponses.Section(s.getTitle(),
							s.getLessons()
								.stream()
								.map((l) -> new CatalogResponses.LessonOutline(l.getId(), l.getTitle(), l.getType(),
										l.getDurationSeconds(), l.isPreview()))
								.toList()))
					.toList());
	}

	/**
	 * Opens a free preview lesson. Other lessons need a purchase (phase 3), so they are
	 * refused here for everyone.
	 */
	@Transactional(readOnly = true)
	public CatalogResponses.PreviewLesson previewLesson(String slug, long lessonId) {
		Lesson lesson = findPublished(slug).getSections()
			.stream()
			.flatMap((s) -> s.getLessons().stream())
			.filter((l) -> l.getId() == lessonId)
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("Lesson not found"));
		if (!lesson.isPreview()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "This lesson is available after buying the course");
		}
		return new CatalogResponses.PreviewLesson(lesson.getId(), lesson.getTitle(), lesson.getType(),
				lesson.getContentText(), lesson.getAudioRef(), lesson.getDurationSeconds());
	}

	/** Drafts and archived courses answer 404, the same as courses that do not exist. */
	private Course findPublished(String slug) {
		return this.courses.findBySlugAndStatus(slug, CourseStatus.PUBLISHED)
			.orElseThrow(() -> ApiException.notFound("Course not found"));
	}

	private static int totalDuration(Course course) {
		return course.getSections()
			.stream()
			.flatMap((s) -> s.getLessons().stream())
			.map(Lesson::getDurationSeconds)
			.filter(Objects::nonNull)
			.mapToInt(Integer::intValue)
			.sum();
	}

}
