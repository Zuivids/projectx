package lv.lms.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	/** Emails are stored normalized (trimmed, lower case); pass a normalized value. */
	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

}
