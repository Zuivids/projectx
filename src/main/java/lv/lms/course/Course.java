package lv.lms.course;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lv.lms.common.ApiException;

/**
 * A course and its content. Sections and lessons are changed only through this class, so
 * their order (position) always stays consistent.
 */
@Entity
@Table(name = "course")
public class Course {

	public static final String DEFAULT_CURRENCY = "EUR";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, length = Slugs.MAX_LENGTH)
	private String slug;

	@Column(nullable = false, columnDefinition = "text")
	private String description;

	@Column(name = "price_cents", nullable = false)
	private long priceCents;

	@Column(nullable = false, length = 3)
	private String currency = DEFAULT_CURRENCY;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private CourseLevel level;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CourseStatus status;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	@OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position")
	private List<CourseSection> sections = new ArrayList<>();

	protected Course() {
	}

	public Course(String title, String slug, String description, long priceCents, CourseLevel level, Instant now) {
		this.title = title;
		this.slug = slug;
		this.description = description;
		this.priceCents = priceCents;
		this.level = level;
		this.status = CourseStatus.DRAFT;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void update(String title, String slug, String description, long priceCents, CourseLevel level,
			Instant now) {
		this.title = title;
		this.slug = slug;
		this.description = description;
		this.priceCents = priceCents;
		this.level = level;
		this.updatedAt = now;
	}

	public void publish(Instant now) {
		requireStatus(CourseStatus.DRAFT, "Only a draft can be published");
		if (lessonCount() == 0) {
			throw ApiException.conflict("A course needs at least one lesson before it can be published");
		}
		this.status = CourseStatus.PUBLISHED;
		if (this.publishedAt == null) {
			this.publishedAt = now;
		}
		this.updatedAt = now;
	}

	public void archive(Instant now) {
		requireStatus(CourseStatus.PUBLISHED, "Only a published course can be archived");
		this.status = CourseStatus.ARCHIVED;
		this.updatedAt = now;
	}

	public void restoreToDraft(Instant now) {
		requireStatus(CourseStatus.ARCHIVED, "Only an archived course can be restored");
		this.status = CourseStatus.DRAFT;
		this.updatedAt = now;
	}

	/** Courses that were ever published must be archived instead, so later purchases keep their course. */
	public boolean canBeDeleted() {
		return this.status == CourseStatus.DRAFT && this.publishedAt == null;
	}

	public CourseSection addSection(String title, Instant now) {
		CourseSection section = new CourseSection(this, title, this.sections.size());
		this.sections.add(section);
		this.updatedAt = now;
		return section;
	}

	public void removeSection(CourseSection section, Instant now) {
		this.sections.remove(section);
		Positions.renumber(this.sections);
		this.updatedAt = now;
	}

	public void reorderSections(List<Long> orderedIds, Instant now) {
		Positions.reorder(this.sections, orderedIds);
		this.updatedAt = now;
	}

	/** Marks the course as changed, e.g. after a lesson was edited. */
	public void touch(Instant now) {
		this.updatedAt = now;
	}

	public int lessonCount() {
		return this.sections.stream().mapToInt((s) -> s.getLessons().size()).sum();
	}

	public boolean isPublished() {
		return this.status == CourseStatus.PUBLISHED;
	}

	private void requireStatus(CourseStatus expected, String message) {
		if (this.status != expected) {
			throw ApiException.conflict(message);
		}
	}

	public Long getId() {
		return this.id;
	}

	public String getTitle() {
		return this.title;
	}

	public String getSlug() {
		return this.slug;
	}

	public String getDescription() {
		return this.description;
	}

	public long getPriceCents() {
		return this.priceCents;
	}

	public String getCurrency() {
		return this.currency;
	}

	public CourseLevel getLevel() {
		return this.level;
	}

	public CourseStatus getStatus() {
		return this.status;
	}

	public Instant getPublishedAt() {
		return this.publishedAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

	public long getVersion() {
		return this.version;
	}

	public List<CourseSection> getSections() {
		return List.copyOf(this.sections);
	}

}
