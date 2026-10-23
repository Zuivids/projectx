package lv.lms.course;

/**
 * Lifecycle of a course (ADM-05). There is no "in review" step, because the team only
 * publishes its own courses (no external instructors).
 *
 * <pre>
 * DRAFT ──publish──▶ PUBLISHED ──archive──▶ ARCHIVED ──restore──▶ DRAFT
 * </pre>
 */
public enum CourseStatus {

	/** Being prepared; not visible to learners. */
	DRAFT,

	/** In the public catalog. */
	PUBLISHED,

	/** Withdrawn from the catalog. Kept, because later purchases will refer to it. */
	ARCHIVED

}
