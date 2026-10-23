package lv.lms.audit;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Insert and read only: there is deliberately no update or delete method. */
public interface AuditLogRepository extends Repository<AuditLogEntry, Long> {

	AuditLogEntry save(AuditLogEntry entry);

	List<AuditLogEntry> findByUserIdOrderByOccurredAtAsc(Long userId);

	/** A user's own actions and admin actions on their account, newest first. */
	@Query("""
			select e from AuditLogEntry e
			where e.userId = :userId or e.targetUserId = :userId
			order by e.occurredAt desc, e.id desc
			""")
	List<AuditLogEntry> findActivity(Long userId, Pageable pageable);

}
