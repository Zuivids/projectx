package lv.lms.user;

import java.util.UUID;

import lv.lms.IntegrationTest;
import lv.lms.MutableClock;
import lv.lms.audit.AuditService;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class LocalAdminSeedTests {

	@Autowired
	private UserRepository users;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AuditService audit;

	@Autowired
	private MutableClock clock;

	@Test
	void createsSuperAdminOnceAndKeepsAChangedPassword() {
		String email = "local-" + UUID.randomUUID() + "@projectx.test";
		LocalAdminSeed seed = seed(email.toUpperCase(), "first password!!");
		seed.run(null);

		User created = this.users.findByEmail(email).orElseThrow();
		assertThat(created.getRoles()).contains(Role.SUPER_ADMIN, Role.LEARNER);
		assertThat(this.passwordEncoder.matches("first password!!", created.getPasswordHash())).isTrue();

		// A second start with another password does not overwrite the existing account
		seed(email, "second password!").run(null);
		assertThat(this.passwordEncoder.matches("first password!!",
				this.users.findByEmail(email).orElseThrow().getPasswordHash())).isTrue();
	}

	@Test
	void doesNothingWithoutEmailAndPassword() {
		long before = this.users.count();
		seed("", "").run(null);
		seed(null, null).run(null);
		assertThat(this.users.count()).isEqualTo(before);
	}

	private LocalAdminSeed seed(String email, String password) {
		return new LocalAdminSeed(new LocalAdminSeed.Properties(email, password), this.users, this.passwordEncoder,
				this.audit, this.clock);
	}

}
