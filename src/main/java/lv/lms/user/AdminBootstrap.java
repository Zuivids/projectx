package lv.lms.user;

import java.time.Clock;
import java.util.List;
import java.util.Locale;

import lv.lms.audit.AuditEventType;
import lv.lms.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gives the super admin role to the accounts listed in {@code app.bootstrap.admin-emails} when
 * the application starts. This is how the first super admin is created; after that, super
 * admins appoint other admins in the admin panel. The accounts must already be registered, and
 * they still need MFA before the admin panel opens.
 */
@Component
class AdminBootstrap implements ApplicationRunner {

	private static final Logger logger = LoggerFactory.getLogger(AdminBootstrap.class);

	private final Properties properties;

	private final UserRepository users;

	private final AuditService audit;

	private final Clock clock;

	AdminBootstrap(Properties properties, UserRepository users, AuditService audit, Clock clock) {
		this.properties = properties;
		this.users = users;
		this.audit = audit;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		for (String email : this.properties.adminEmails()) {
			String normalized = email.strip().toLowerCase(Locale.ROOT);
			this.users.findByEmail(normalized).ifPresentOrElse((user) -> {
				if (!user.hasRole(Role.SUPER_ADMIN)) {
					user.grantRole(Role.SUPER_ADMIN, this.clock.instant());
					this.users.save(user);
					this.audit.record(AuditEventType.ADMIN_ROLE_GRANTED, user.getId(), null,
							"SUPER_ADMIN granted at startup by app.bootstrap.admin-emails");
					logger.info("Granted the super admin role to user {}", user.getId());
				}
			}, () -> logger.warn("app.bootstrap.admin-emails lists an email that is not registered yet"));
		}
	}

	/** @param adminEmails accounts that get the admin role at startup */
	@ConfigurationProperties("app.bootstrap")
	record Properties(List<String> adminEmails) {

		Properties {
			adminEmails = (adminEmails != null) ? List.copyOf(adminEmails) : List.of();
		}

	}

}
