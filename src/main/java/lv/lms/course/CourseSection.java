package lv.lms.course;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "course_section")
public class CourseSection implements Positions.Positioned {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "course_id")
	private Course course;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false)
	private int position;

	@OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position")
	// Loads the lessons of up to 50 sections in one query instead of one query each
	@BatchSize(size = 50)
	private List<Lesson> lessons = new ArrayList<>();

	protected CourseSection() {
	}

	CourseSection(Course course, String title, int position) {
		this.course = course;
		this.title = title;
		this.position = position;
	}

	public void rename(String title) {
		this.title = title;
	}

	public Lesson addLesson(Lesson.Content content) {
		Lesson lesson = new Lesson(this, content, this.lessons.size());
		this.lessons.add(lesson);
		return lesson;
	}

	public void removeLesson(Lesson lesson) {
		this.lessons.remove(lesson);
		Positions.renumber(this.lessons);
	}

	public void reorderLessons(List<Long> orderedIds) {
		Positions.reorder(this.lessons, orderedIds);
	}

	@Override
	public Long getId() {
		return this.id;
	}

	public Course getCourse() {
		return this.course;
	}

	public String getTitle() {
		return this.title;
	}

	public int getPosition() {
		return this.position;
	}

	@Override
	public void setPosition(int position) {
		this.position = position;
	}

	public List<Lesson> getLessons() {
		return List.copyOf(this.lessons);
	}

}
