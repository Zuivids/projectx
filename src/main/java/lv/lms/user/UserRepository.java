package lv.lms.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

	/** Emails are stored normalized (trimmed, lower case); pass a normalized value. */
	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	/** Status only, without loading the whole user; checked on every authenticated request. */
	@Query("select u.status from User u where u.id = :id")
	Optional<UserStatus> findStatusById(Long id);

}
