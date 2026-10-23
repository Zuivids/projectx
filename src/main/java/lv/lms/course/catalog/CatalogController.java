package lv.lms.course.catalog;

import lv.lms.common.PageResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public course catalog; no login needed (opened in SecurityConfig). */
@RestController
@RequestMapping("/api/v1/courses")
class CatalogController {

	private final CatalogService service;

	CatalogController(CatalogService service) {
		this.service = service;
	}

	@GetMapping
	PageResponse<CatalogResponses.CourseSummary> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return this.service.list(page, size);
	}

	@GetMapping("/{slug}")
	CatalogResponses.CourseDetail get(@PathVariable String slug) {
		return this.service.get(slug);
	}

	@GetMapping("/{slug}/lessons/{lessonId}")
	CatalogResponses.PreviewLesson previewLesson(@PathVariable String slug, @PathVariable long lessonId) {
		return this.service.previewLesson(slug, lessonId);
	}

}
