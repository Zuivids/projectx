package lv.lms.course.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SectionRequest(@NotBlank @Size(max = 200) String title) {
}
