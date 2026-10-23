package lv.lms.user;

import java.util.List;
import java.util.UUID;

import lv.lms.IntegrationTest;
import lv.lms.MutableClock;
import lv.lms.audit.AuditEventType;
import lv.lms.audit.AuditLogEntry;
import lv.lms.audit.AuditLogRepository;
import lv.lms.audit.AuditService;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class AdminBootstrapTests {

	@Autowired
	private UserRepository users;

	@Autowired
	private AuditService audit;

	@Autowired
	private AuditLogRepository auditLog;

	@Autowired
	private MutableClock clock;

	@Test
	void grantsAdminOnceToListedRegisteredAccounts() {
		String email = "boss-" + UUID.randomUUID() + "@example.com";
		User user = this.users.save(new User(email, "{noop}unused", "Boss", Role.LEARNER, this.clock.instant()));

		// Listed in a different case, plus an email that is not registered
		AdminBootstrap bootstrap = new AdminBootstrap(
				new AdminBootstrap.Properties(List.of(email.toUpperCase(), "nobody@example.com")), this.users,
				this.audit, this.clock);
		bootstrap.run(null);
		bootstrap.run(null);

		assertThat(this.users.findById(user.getId()).orElseThrow().getRoles()).contains(Role.ADMIN, Role.LEARNER);
		assertThat(this.auditLog.findByUserIdOrderByOccurredAtAsc(user.getId())
			.stream()
			.map(AuditLogEntry::getEventType)
			.filter(AuditEventType.ADMIN_ROLE_GRANTED::equals)).hasSize(1);
	}

	@Test
	void doesNothingWithoutConfiguration() {
		assertThat(new AdminBootstrap.Properties(null).adminEmails()).isEmpty();
	}

}
