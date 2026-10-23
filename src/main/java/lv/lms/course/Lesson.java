package lv.lms.course;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "lesson")
public class Lesson implements Positions.Positioned {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "section_id")
	private CourseSection section;

	@Column(nullable = false, length = 200)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LessonType type;

	@Column(name = "content_text", columnDefinition = "mediumtext")
	private String contentText;

	@Column(name = "audio_ref", length = 500)
	private String audioRef;

	@Column(name = "duration_seconds")
	private Integer durationSeconds;

	@Column(nullable = false)
	private boolean preview;

	@Column(nullable = false)
	private int position;

	protected Lesson() {
	}

	Lesson(CourseSection section, Content content, int position) {
		this.section = section;
		this.position = position;
		update(content);
	}

	public void update(Content content) {
		this.title = content.title();
		this.type = content.type();
		this.contentText = content.contentText();
		this.audioRef = content.audioRef();
		this.durationSeconds = content.durationSeconds();
		this.preview = content.preview();
	}

	@Override
	public Long getId() {
		return this.id;
	}

	public CourseSection getSection() {
		return this.section;
	}

	public String getTitle() {
		return this.title;
	}

	public LessonType getType() {
		return this.type;
	}

	public String getContentText() {
		return this.contentText;
	}

	public String getAudioRef() {
		return this.audioRef;
	}

	public Integer getDurationSeconds() {
		return this.durationSeconds;
	}

	public boolean isPreview() {
		return this.preview;
	}

	public int getPosition() {
		return this.position;
	}

	@Override
	public void setPosition(int position) {
		this.position = position;
	}

	/** Editable fields of a lesson. */
	public record Content(String title, LessonType type, String contentText, String audioRef,
			Integer durationSeconds, boolean preview) {
	}

}
