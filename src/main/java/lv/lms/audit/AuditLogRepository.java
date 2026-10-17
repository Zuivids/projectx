package lv.lms.audit;

import java.util.List;

import org.springframework.data.repository.Repository;

/** Insert and read only: there is deliberately no update or delete method. */
public interface AuditLogRepository extends Repository<AuditLogEntry, Long> {

	AuditLogEntry save(AuditLogEntry entry);

	List<AuditLogEntry> findByUserIdOrderByOccurredAtAsc(Long userId);

}
