package lv.lms.course.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lv.lms.course.Lesson;
import lv.lms.course.LessonType;

/**
 * @param contentText lesson text (text lessons) or notes/transcript (audio lessons); shown
 * as plain text, never as HTML
 * @param audioRef reference to the audio file at the media host (not decided yet, D-06)
 * @param preview free to open without buying the course
 */
public record LessonRequest(
		@NotBlank @Size(max = 200) String title,
		@NotNull LessonType type,
		@Size(max = 1_000_000) String contentText,
		@Size(max = 500) String audioRef,
		@Min(0) @Max(86_400) Integer durationSeconds,
		boolean preview) {

	public Lesson.Content toContent() {
		return new Lesson.Content(this.title.strip(), this.type, blankToNull(this.contentText),
				blankToNull(this.audioRef), this.durationSeconds, this.preview);
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value;
	}

}
