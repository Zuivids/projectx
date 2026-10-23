package lv.lms.course.admin;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import lv.lms.audit.Actor;
import lv.lms.audit.AuditEventType;
import lv.lms.audit.AuditService;
import lv.lms.common.ApiException;
import lv.lms.common.PageResponse;
import lv.lms.course.Course;
import lv.lms.course.CourseRepository;
import lv.lms.course.CourseSection;
import lv.lms.course.CourseStatus;
import lv.lms.course.Lesson;
import lv.lms.course.Slugs;
import lv.lms.course.admin.dto.CourseAdminResponses;
import lv.lms.course.admin.dto.CourseCreateRequest;
import lv.lms.course.admin.dto.CourseUpdateRequest;
import lv.lms.course.admin.dto.LessonRequest;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Course management for admins. Every change is written to the audit log (ADM-09) and
 * returns the whole course, so the editor always shows what was saved.
 */
@Service
public class CourseAdminService {

	private static final int MAX_PAGE_SIZE = 100;

	private final CourseRepository courses;

	private final AuditService audit;

	private final Clock clock;

	CourseAdminService(CourseRepository courses, AuditService audit, Clock clock) {
		this.courses = courses;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResponse<CourseAdminResponses.CourseSummary> list(CourseStatus status, int page, int size) {
		Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
				Sort.by(Sort.Direction.DESC, "updatedAt"));
		return PageResponse.of(
				(status != null) ? this.courses.findByStatus(status, pageable) : this.courses.findAll(pageable),
				CourseAdminService::toSummary);
	}

	@Transactional(readOnly = true)
	public CourseAdminResponses.CourseDetail get(long courseId) {
		return toDetail(find(courseId));
	}

	@Transactional
	public CourseAdminResponses.CourseDetail create(CourseCreateRequest request, Actor actor) {
		String slug;
		if (request.slug() == null || request.slug().isBlank()) {
			slug = uniqueSlug(Slugs.fromTitle(request.title()));
		}
		else {
			requireFreeSlug(request.slug(), null);
			slug = request.slug();
		}
		Course course = this.courses.saveAndFlush(new Course(request.title().strip(), slug,
				request.description(), request.priceCents(), request.level(), this.clock.instant()));
		record(AuditEventType.COURSE_CREATED, actor, course, null);
		return toDetail(course);
	}

	@Transactional
	public CourseAdminResponses.CourseDetail update(long courseId, CourseUpdateRequest request, Actor actor) {
		Course course = find(courseId);
		if (course.getVersion() != request.version()) {
			throw ApiException.conflict("The course was changed by someone else. Reload it and try again.");
		}
		requireFreeSlug(request.slug(), courseId);
		course.update(request.title().strip(), request.slug(), request.description(), request.priceCents(),
				request.level(), this.clock.instant());
		return saveAndRecord(course, AuditEventType.COURSE_UPDATED, actor, null);
	}

	@Transactional
	public void delete(long courseId, Actor actor) {
		Course course = find(courseId);
		if (!course.canBeDeleted()) {
			throw ApiException.conflict("Only drafts that were never published can be deleted. Archive it instead.");
		}
		this.courses.delete(course);
		record(AuditEventType.COURSE_DELETED, actor, course, "title=" + course.getTitle());
	}

	@Transactional
	public CourseAdminResponses.CourseDetail publish(long courseId, Actor actor) {
		return change(courseId, actor, AuditEventType.COURSE_PUBLISHED, null, (c) -> c.publish(now()));
	}

	@Transactional
	public CourseAdminResponses.CourseDetail archive(long courseId, Actor actor) {
		return change(courseId, actor, AuditEventType.COURSE_ARCHIVED, null, (c) -> c.archive(now()));
	}

	@Transactional
	public CourseAdminResponses.CourseDetail restoreToDraft(long courseId, Actor actor) {
		return change(courseId, actor, AuditEventType.COURSE_RESTORED_TO_DRAFT, null,
				(c) -> c.restoreToDraft(now()));
	}

	@Transactional
	public CourseAdminResponses.CourseDetail addSection(long courseId, String title, Actor actor) {
		return change(courseId, actor, AuditEventType.COURSE_CONTENT_CHANGED, "section added",
				(c) -> c.addSection(title.strip(), now()));
	}

	@Transactional
	public CourseAdminResponses.CourseDetail renameSection(long courseId, long sectionId, String title,
			Actor actor) {
		return change(courseId, actor, AuditEventType.COURSE_CONTENT_CHANGED, "section " + sectionId + " renamed",
				(c) -> {
					section(c, sectionId).rename(title.strip());
					c.touch(now());
				});
	}

	@Transactional
	public CourseAdminResponses.CourseDetail deleteSection(long courseId, long sectionId, Actor actor) {
		return change(courseId, actor, AuditEventType.COURSE_CONTENT_CHANGED, "section " + sectionId + " deleted",
				(c) -> {
					c.removeSection(section(c, sectionId), now());
					requireLessonsIfPublished(c);
				});
	}

	@Transactional
	public CourseAdminResponses.CourseDetail reorderSections(long courseId, List<Long> sectionIds, Actor actor) {
		return change(courseId, actor, AuditEventType.COURSE_CONTENT_CHANGED, "sections reordered",
				(c) -> c.reorderSections(sectionIds, now()));
	}

	@Transactional
	public CourseAdminResponses.CourseDetail addLesson(long courseId, long sectionId, LessonRequest request,
			Actor actor) {
		return change(courseId, actor, AuditEventType.COURSE_CONTENT_CHANGED, "lesson added to section " + sectionId,
				(c) -> {
					section(c, sectionId).addLesson(request.toContent());
					c.touch(now());
				});
	}

	@Transactional
	public CourseAdminResponses.CourseDetail updateLesson(long courseId, long lessonId, LessonRequest request,
			Actor actor) {
		return change(courseId, actor, AuditEventType.COURSE_CONTENT_CHANGED, "lesson " + lessonId + " updated",
				(c) -> {
					lesson(c, lessonId).update(request.toContent());
					c.touch(now());
				});
	}

	@Transactional
	public CourseAdminResponses.CourseDetail deleteLesson(long courseId, long lessonId, Actor actor) {
		return change(courseId, actor, AuditEventType.COURSE_CONTENT_CHANGED, "lesson " + lessonId + " deleted",
				(c) -> {
					Lesson lesson = lesson(c, lessonId);
					lesson.getSection().removeLesson(lesson);
					c.touch(now());
					requireLessonsIfPublished(c);
				});
	}

	@Transactional
	public CourseAdminResponses.CourseDetail reorderLessons(long courseId, long sectionId, List<Long> lessonIds,
			Actor actor) {
		return change(courseId, actor, AuditEventType.COURSE_CONTENT_CHANGED,
				"lessons of section " + sectionId + " reordered", (c) -> {
					section(c, sectionId).reorderLessons(lessonIds);
					c.touch(now());
				});
	}

	private CourseAdminResponses.CourseDetail change(long courseId, Actor actor, AuditEventType event,
			String details, Consumer<Course> action) {
		Course course = find(courseId);
		action.accept(course);
		return saveAndRecord(course, event, actor, details);
	}

	private CourseAdminResponses.CourseDetail saveAndRecord(Course course, AuditEventType event, Actor actor,
			String details) {
		// Flush, so new sections and lessons have their ids in the response
		Course saved = this.courses.saveAndFlush(course);
		record(event, actor, saved, details);
		return toDetail(saved);
	}

	private void record(AuditEventType event, Actor actor, Course course, String details) {
		String text = "course " + course.getId() + ((details != null) ? ": " + details : "");
		this.audit.record(event, actor.userId(), actor.ipAddress(), text);
	}

	/** A published course must keep at least one lesson; archive it first to empty it. */
	private static void requireLessonsIfPublished(Course course) {
		if (course.isPublished() && course.lessonCount() == 0) {
			throw ApiException.conflict("A published course must keep at least one lesson");
		}
	}

	private Course find(long courseId) {
		return this.courses.findById(courseId).orElseThrow(() -> ApiException.notFound("Course not found"));
	}

	/** Looks up the section inside this course, so ids from another course are rejected. */
	private static CourseSection section(Course course, long sectionId) {
		return course.getSections()
			.stream()
			.filter((s) -> s.getId() == sectionId)
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("Section not found in this course"));
	}

	private static Lesson lesson(Course course, long lessonId) {
		return course.getSections()
			.stream()
			.flatMap((s) -> s.getLessons().stream())
			.filter((l) -> l.getId() == lessonId)
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("Lesson not found in this course"));
	}

	private void requireFreeSlug(String slug, Long courseId) {
		boolean taken = (courseId == null) ? this.courses.existsBySlug(slug)
				: this.courses.existsBySlugAndIdNot(slug, courseId);
		if (taken) {
			throw ApiException.conflict("Another course already uses this address (slug)");
		}
	}

	/** "latviesu-valoda", then "latviesu-valoda-2", "-3", … */
	private String uniqueSlug(String base) {
		String candidate = base;
		for (int n = 2; this.courses.existsBySlug(candidate); n++) {
			String suffix = "-" + n;
			candidate = base.substring(0, Math.min(base.length(), Slugs.MAX_LENGTH - suffix.length())) + suffix;
		}
		return candidate;
	}

	private Instant now() {
		return this.clock.instant();
	}

	private static CourseAdminResponses.CourseSummary toSummary(Course course) {
		return new CourseAdminResponses.CourseSummary(course.getId(), course.getTitle(), course.getSlug(),
				course.getStatus(), course.getPriceCents(), course.getCurrency(), course.getLevel(),
				course.getSections().size(), course.lessonCount(), course.getUpdatedAt());
	}

	private static CourseAdminResponses.CourseDetail toDetail(Course course) {
		List<CourseAdminResponses.Section> sections = course.getSections()
			.stream()
			.map((s) -> new CourseAdminResponses.Section(s.getId(), s.getTitle(), s.getPosition(),
					s.getLessons()
						.stream()
						.map((l) -> new CourseAdminResponses.Lesson(l.getId(), l.getTitle(), l.getType(),
								l.getContentText(), l.getAudioRef(), l.getDurationSeconds(), l.isPreview(),
								l.getPosition()))
						.toList()))
			.toList();
		return new CourseAdminResponses.CourseDetail(course.getId(), course.getTitle(), course.getSlug(),
				course.getDescription(), course.getPriceCents(), course.getCurrency(), course.getLevel(),
				course.getStatus(), course.getVersion(), course.getPublishedAt(), course.getUpdatedAt(),
				course.canBeDeleted(), sections);
	}

}
