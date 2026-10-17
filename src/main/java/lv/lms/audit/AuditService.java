package lv.lms.audit;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

	private final AuditLogRepository repository;

	private final Clock clock;

	public AuditService(AuditLogRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/**
	 * Records a security event in the caller's transaction (or a new one if there is
	 * none). Never pass passwords, codes, tokens or other secrets as details.
	 */
	@Transactional(propagation = Propagation.REQUIRED)
	public void record(AuditEventType type, Long userId, String ipAddress, String details) {
		this.repository.save(new AuditLogEntry(this.clock.instant(), type, userId, ipAddress, details));
	}

}
