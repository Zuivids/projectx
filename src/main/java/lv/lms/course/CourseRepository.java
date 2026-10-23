package lv.lms.course;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {

	Page<Course> findByStatus(CourseStatus status, Pageable pageable);

	Optional<Course> findBySlugAndStatus(String slug, CourseStatus status);

	boolean existsBySlug(String slug);

	boolean existsBySlugAndIdNot(String slug, Long id);

}
