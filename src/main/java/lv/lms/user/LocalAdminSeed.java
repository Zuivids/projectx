package lv.lms.user;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

import lv.lms.audit.AuditEventType;
import lv.lms.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local development only: creates a super admin account from LOCAL_ADMIN_EMAIL and
 * LOCAL_ADMIN_PASSWORD (written to .env by the start script), so a fresh local database
 * is usable right away. Exists only with the "local" profile, never in production.
 */
@Component
@Profile("local")
class LocalAdminSeed implements ApplicationRunner {

	private static final Logger logger = LoggerFactory.getLogger(LocalAdminSeed.class);

	private final Properties properties;

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final AuditService audit;

	private final Clock clock;

	LocalAdminSeed(Properties properties, UserRepository users, PasswordEncoder passwordEncoder, AuditService audit,
			Clock clock) {
		this.properties = properties;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.audit = audit;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (isBlank(this.properties.adminEmail()) || isBlank(this.properties.adminPassword())) {
			return;
		}
		String email = this.properties.adminEmail().strip().toLowerCase(Locale.ROOT);
		Instant now = this.clock.instant();
		User user = this.users.findByEmail(email).orElseGet(() -> {
			User created = this.users.save(new User(email, this.passwordEncoder.encode(this.properties.adminPassword()),
					"Local Admin", Role.LEARNER, now));
			this.audit.record(AuditEventType.USER_REGISTERED, created.getId(), null, "local seed");
			logger.info("Created local super admin {}", email);
			return created;
		});
		if (!user.hasRole(Role.SUPER_ADMIN)) {
			user.grantRole(Role.SUPER_ADMIN, now);
			this.users.save(user);
			this.audit.record(AuditEventType.ADMIN_ROLE_GRANTED, user.getId(), null, "SUPER_ADMIN by local seed");
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	/**
	 * @param adminEmail email of the local super admin
	 * @param adminPassword its password; used only when the account is first created
	 */
	@ConfigurationProperties("app.local-seed")
	record Properties(String adminEmail, String adminPassword) {
	}

}
