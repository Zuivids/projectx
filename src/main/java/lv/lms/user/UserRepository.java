package lv.lms.user;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

	/** Emails are stored normalized (trimmed, lower case); pass a normalized value. */
	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	/** Status and security version only; checked on every authenticated request. */
	@Query("select new lv.lms.user.UserSessionState(u.status, u.securityVersion) from User u where u.id = :id")
	Optional<UserSessionState> findSessionState(Long id);

	/**
	 * Admin user search. {@code search} is a lowercase LIKE pattern such as "%anna%", or
	 * null for all users.
	 */
	@Query("""
			select u from User u
			where (:search is null or lower(u.email) like :search or lower(u.displayName) like :search)
			and (:status is null or u.status = :status)
			""")
	Page<User> search(String search, UserStatus status, Pageable pageable);

}
