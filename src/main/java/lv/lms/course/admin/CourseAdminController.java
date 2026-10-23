package lv.lms.course.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lv.lms.audit.Actor;
import lv.lms.auth.AuthenticatedUser;
import lv.lms.common.PageResponse;
import lv.lms.course.CourseStatus;
import lv.lms.course.admin.dto.CourseAdminResponses.CourseDetail;
import lv.lms.course.admin.dto.CourseAdminResponses.CourseSummary;
import lv.lms.course.admin.dto.CourseCreateRequest;
import lv.lms.course.admin.dto.CourseUpdateRequest;
import lv.lms.course.admin.dto.LessonRequest;
import lv.lms.course.admin.dto.ReorderRequest;
import lv.lms.course.admin.dto.SectionRequest;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Course management. Under /api/v1/admin, so SecurityConfig requires an admin role and an
 * MFA-verified session for every endpoint here.
 */
@RestController
@RequestMapping("/api/v1/admin/courses")
class CourseAdminController {

	private final CourseAdminService service;

	CourseAdminController(CourseAdminService service) {
		this.service = service;
	}

	@GetMapping
	PageResponse<CourseSummary> list(@RequestParam(required = false) CourseStatus status,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return this.service.list(status, page, size);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	CourseDetail create(@Valid @RequestBody CourseCreateRequest body, @AuthenticationPrincipal AuthenticatedUser user,
			HttpServletRequest request) {
		return this.service.create(body, actor(user, request));
	}

	@GetMapping("/{courseId}")
	CourseDetail get(@PathVariable long courseId) {
		return this.service.get(courseId);
	}

	@PutMapping("/{courseId}")
	CourseDetail update(@PathVariable long courseId, @Valid @RequestBody CourseUpdateRequest body,
			@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
		return this.service.update(courseId, body, actor(user, request));
	}

	@DeleteMapping("/{courseId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable long courseId, @AuthenticationPrincipal AuthenticatedUser user,
			HttpServletRequest request) {
		this.service.delete(courseId, actor(user, request));
	}

	@PostMapping("/{courseId}/publish")
	CourseDetail publish(@PathVariable long courseId, @AuthenticationPrincipal AuthenticatedUser user,
			HttpServletRequest request) {
		return this.service.publish(courseId, actor(user, request));
	}

	@PostMapping("/{courseId}/archive")
	CourseDetail archive(@PathVariable long courseId, @AuthenticationPrincipal AuthenticatedUser user,
			HttpServletRequest request) {
		return this.service.archive(courseId, actor(user, request));
	}

	@PostMapping("/{courseId}/restore")
	CourseDetail restore(@PathVariable long courseId, @AuthenticationPrincipal AuthenticatedUser user,
			HttpServletRequest request) {
		return this.service.restoreToDraft(courseId, actor(user, request));
	}

	@PostMapping("/{courseId}/sections")
	CourseDetail addSection(@PathVariable long courseId, @Valid @RequestBody SectionRequest body,
			@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
		return this.service.addSection(courseId, body.title(), actor(user, request));
	}

	@PutMapping("/{courseId}/sections/order")
	CourseDetail reorderSections(@PathVariable long courseId, @Valid @RequestBody ReorderRequest body,
			@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
		return this.service.reorderSections(courseId, body.ids(), actor(user, request));
	}

	@PutMapping("/{courseId}/sections/{sectionId}")
	CourseDetail renameSection(@PathVariable long courseId, @PathVariable long sectionId,
			@Valid @RequestBody SectionRequest body, @AuthenticationPrincipal AuthenticatedUser user,
			HttpServletRequest request) {
		return this.service.renameSection(courseId, sectionId, body.title(), actor(user, request));
	}

	@DeleteMapping("/{courseId}/sections/{sectionId}")
	CourseDetail deleteSection(@PathVariable long courseId, @PathVariable long sectionId,
			@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
		return this.service.deleteSection(courseId, sectionId, actor(user, request));
	}

	@PostMapping("/{courseId}/sections/{sectionId}/lessons")
	CourseDetail addLesson(@PathVariable long courseId, @PathVariable long sectionId,
			@Valid @RequestBody LessonRequest body, @AuthenticationPrincipal AuthenticatedUser user,
			HttpServletRequest request) {
		return this.service.addLesson(courseId, sectionId, body, actor(user, request));
	}

	@PutMapping("/{courseId}/sections/{sectionId}/lessons/order")
	CourseDetail reorderLessons(@PathVariable long courseId, @PathVariable long sectionId,
			@Valid @RequestBody ReorderRequest body, @AuthenticationPrincipal AuthenticatedUser user,
			HttpServletRequest request) {
		return this.service.reorderLessons(courseId, sectionId, body.ids(), actor(user, request));
	}

	@PutMapping("/{courseId}/lessons/{lessonId}")
	CourseDetail updateLesson(@PathVariable long courseId, @PathVariable long lessonId,
			@Valid @RequestBody LessonRequest body, @AuthenticationPrincipal AuthenticatedUser user,
			HttpServletRequest request) {
		return this.service.updateLesson(courseId, lessonId, body, actor(user, request));
	}

	@DeleteMapping("/{courseId}/lessons/{lessonId}")
	CourseDetail deleteLesson(@PathVariable long courseId, @PathVariable long lessonId,
			@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
		return this.service.deleteLesson(courseId, lessonId, actor(user, request));
	}

	private static Actor actor(AuthenticatedUser user, HttpServletRequest request) {
		return new Actor(user.id(), request.getRemoteAddr());
	}

}
