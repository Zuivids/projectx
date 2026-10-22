package lv.lms.auth;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import lv.lms.IntegrationTest;
import lv.lms.MutableClock;
import lv.lms.audit.AuditEventType;
import lv.lms.audit.AuditLogEntry;
import lv.lms.audit.AuditLogRepository;
import lv.lms.auth.mfa.Base32;
import lv.lms.auth.mfa.Totp;
import lv.lms.user.Role;
import lv.lms.user.User;
import lv.lms.user.UserRepository;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AuthFlowTests {

	private static final String PASSWORD = "correct horse battery";

	/** JUnit creates a new instance per test, so every test gets its own IP. */
	private final String ip = randomIp();

	@Autowired
	private MockMvc mvc;

	@Autowired
	private MutableClock clock;

	@Autowired
	private UserRepository users;

	@Autowired
	private AuditLogRepository auditLog;

	@Test
	void registerLoginAndReadProfile() throws Exception {
		String email = register();
		MockHttpSession session = new MockHttpSession();
		login(session, email, PASSWORD).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("AUTHENTICATED"))
			.andExpect(jsonPath("$.mfaSetupRequired").value(false));
		this.mvc.perform(get("/api/v1/me").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.roles[0]").value("LEARNER"))
			.andExpect(jsonPath("$.mfaEnabled").value(false));
		assertThat(this.users.findByEmail(email).orElseThrow().getPasswordHash()).startsWith("{argon2}");
	}

	@Test
	void emailIsCaseInsensitiveAndDuplicateRegistrationRevealsNothing() throws Exception {
		String email = register();
		registerRequest(email.toUpperCase(), "another long password").andExpect(status().isAccepted());
		assertThat(this.users.findAll().stream().filter((u) -> u.getEmail().equals(email))).hasSize(1);
		login(new MockHttpSession(), email.toUpperCase(), PASSWORD).andExpect(status().isOk());
	}

	@Test
	void rejectsInvalidRegistration() throws Exception {
		registerRequest("not-an-email", PASSWORD).andExpect(status().isBadRequest());
		registerRequest(uniqueEmail(), "too short").andExpect(status().isBadRequest());
	}

	@Test
	void unknownEmailAndWrongPasswordGetTheSameAnswer() throws Exception {
		String email = register();
		String unknown = login(new MockHttpSession(), uniqueEmail(), PASSWORD).andExpect(status().isUnauthorized())
			.andReturn().getResponse().getContentAsString();
		String wrong = login(new MockHttpSession(), email, "wrong password!!").andExpect(status().isUnauthorized())
			.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<String>read(unknown, "$.detail")).isEqualTo(JsonPath.read(wrong, "$.detail"));
	}

	@Test
	void fiveFailuresBlockOnlyTheAttackersIp() throws Exception {
		String email = register();
		for (int i = 0; i < 5; i++) {
			login(new MockHttpSession(), email, "wrong password!!").andExpect(status().isUnauthorized());
		}
		// Correct password is refused from the attacker's IP, with the same generic message
		login(new MockHttpSession(), email, PASSWORD).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Invalid email or password"));
		assertThat(eventsFor(email)).contains(AuditEventType.BLOCKED_FOR_IP).doesNotContain(AuditEventType.ACCOUNT_LOCKED);

		// The real user on another IP is not affected
		loginFrom(randomIp(), email, PASSWORD).andExpect(status().isOk());

		this.clock.advance(Duration.ofMinutes(16));
		login(new MockHttpSession(), email, PASSWORD).andExpect(status().isOk());
	}

	@Test
	void manyFailuresFromManyIpsLockTheWholeAccount() throws Exception {
		String email = register();
		// 20 failures spread over 5 addresses, each staying under the per-IP limit of 5
		for (int i = 0; i < 5; i++) {
			String attackerIp = randomIp();
			for (int j = 0; j < 4; j++) {
				loginFrom(attackerIp, email, "wrong password!!").andExpect(status().isUnauthorized());
			}
		}
		loginFrom(randomIp(), email, PASSWORD).andExpect(status().isUnauthorized());
		assertThat(eventsFor(email)).contains(AuditEventType.ACCOUNT_LOCKED);

		this.clock.advance(Duration.ofMinutes(16));
		loginFrom(randomIp(), email, PASSWORD).andExpect(status().isOk());
	}

	@Test
	void protectedEndpointsRequireLoginAndCsrf() throws Exception {
		this.mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
		this.mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content(loginJson(uniqueEmail(), PASSWORD))).andExpect(status().isForbidden());
	}

	@Test
	void mfaEnrollmentAndLoginWithAuthenticatorCode() throws Exception {
		String email = register();
		MockHttpSession session = new MockHttpSession();
		login(session, email, PASSWORD).andExpect(status().isOk());
		byte[] secret = enrollTotp(session);

		this.mvc.perform(get("/api/v1/me").session(session))
			.andExpect(jsonPath("$.mfaEnabled").value(true))
			.andExpect(jsonPath("$.mfaVerified").value(true));
		logout(session);

		// Password alone no longer logs in
		MockHttpSession next = new MockHttpSession();
		login(next, email, PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("MFA_REQUIRED"));
		this.mvc.perform(get("/api/v1/me").session(next)).andExpect(status().isUnauthorized());

		mfa(next, "000000").andExpect(status().isUnauthorized());
		this.clock.advance(Duration.ofSeconds(Totp.PERIOD_SECONDS));
		String code = currentCode(secret);
		mfa(next, code).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AUTHENTICATED"));
		this.mvc.perform(get("/api/v1/me").session(next))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mfaVerified").value(true));
		logout(next);

		// The same code cannot be replayed
		MockHttpSession replay = new MockHttpSession();
		login(replay, email, PASSWORD).andExpect(jsonPath("$.status").value("MFA_REQUIRED"));
		mfa(replay, code).andExpect(status().isUnauthorized());
	}

	@Test
	void recoveryCodeWorksOnce() throws Exception {
		String email = register();
		MockHttpSession session = new MockHttpSession();
		login(session, email, PASSWORD);
		String confirm = enrollTotpResponse(session);
		List<String> codes = JsonPath.read(confirm, "$.recoveryCodes");
		assertThat(codes).hasSize(10).doesNotHaveDuplicates().allMatch((c) -> c.matches("[A-Z2-9]{5}-[A-Z2-9]{5}"));
		logout(session);

		MockHttpSession first = new MockHttpSession();
		login(first, email, PASSWORD);
		// Formatting does not matter
		mfa(first, codes.get(0).toLowerCase().replace("-", " ")).andExpect(status().isOk());
		logout(first);

		MockHttpSession second = new MockHttpSession();
		login(second, email, PASSWORD);
		mfa(second, codes.get(0)).andExpect(status().isUnauthorized());
		assertThat(eventsFor(email)).contains(AuditEventType.RECOVERY_CODE_USED);
	}

	@Test
	void mfaFailuresLockTheAccountEvenAcrossLogins() throws Exception {
		String email = register();
		MockHttpSession session = new MockHttpSession();
		login(session, email, PASSWORD);
		enrollTotp(session);
		logout(session);

		// Attacker knows the password and starts a new login for each guess
		for (int i = 0; i < 5; i++) {
			MockHttpSession attempt = new MockHttpSession();
			login(attempt, email, PASSWORD).andExpect(jsonPath("$.status").value("MFA_REQUIRED"));
			mfa(attempt, "123456").andExpect(status().isUnauthorized());
		}
		login(new MockHttpSession(), email, PASSWORD).andExpect(status().isUnauthorized());
	}

	@Test
	void mfaStepExpiresAndCannotBeSkipped() throws Exception {
		this.mvc.perform(post("/api/v1/auth/mfa").with(browser()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"code\":\"123456\"}")).andExpect(status().isUnauthorized());

		String email = register();
		MockHttpSession session = new MockHttpSession();
		login(session, email, PASSWORD);
		byte[] secret = enrollTotp(session);
		logout(session);

		MockHttpSession pending = new MockHttpSession();
		login(pending, email, PASSWORD);
		this.clock.advance(Duration.ofMinutes(6));
		mfa(pending, currentCode(secret)).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Login expired, please log in again"));
	}

	@Test
	void adminEndpointsNeedAdminRoleAndMfa() throws Exception {
		String email = register();
		User user = this.users.findByEmail(email).orElseThrow();
		user.grantRole(Role.ADMIN, this.clock.instant());
		this.users.save(user);

		MockHttpSession session = new MockHttpSession();
		login(session, email, PASSWORD).andExpect(jsonPath("$.mfaSetupRequired").value(true));
		this.mvc.perform(get("/api/v1/admin/ping").session(session)).andExpect(status().isForbidden());

		enrollTotp(session);
		// Passes security; 404 only because no admin endpoint exists yet
		this.mvc.perform(get("/api/v1/admin/ping").session(session)).andExpect(status().isNotFound());

		MockHttpSession learner = new MockHttpSession();
		String learnerEmail = register();
		login(learner, learnerEmail, PASSWORD);
		enrollTotp(learner);
		this.mvc.perform(get("/api/v1/admin/ping").session(learner)).andExpect(status().isForbidden());
	}

	@Test
	void csrfCookieRoundTripWorksLikeABrowser() throws Exception {
		String email = register();
		Cookie xsrf = this.mvc.perform(get("/api/v1/auth/csrf"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
			.andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(xsrf).isNotNull();
		assertThat(xsrf.isHttpOnly()).isFalse();

		this.mvc.perform(post("/api/v1/auth/login").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
			.contentType(MediaType.APPLICATION_JSON).content(loginJson(email, PASSWORD))).andExpect(status().isOk());
		this.mvc.perform(post("/api/v1/auth/login").cookie(xsrf).header("X-XSRF-TOKEN", "forged")
			.contentType(MediaType.APPLICATION_JSON).content(loginJson(email, PASSWORD)))
			.andExpect(status().isForbidden());
	}

	@Test
	void sessionIdChangesOnLogin() throws Exception {
		String email = register();
		MockHttpSession session = new MockHttpSession();
		String before = session.getId();
		login(session, email, PASSWORD);
		assertThat(session.getId()).isNotEqualTo(before);
	}

	private byte[] enrollTotp(MockHttpSession session) throws Exception {
		return Base32.decode(JsonPath.read(enrollTotpResponseWithSecret(session)[0], "$.secret"));
	}

	private String enrollTotpResponse(MockHttpSession session) throws Exception {
		return enrollTotpResponseWithSecret(session)[1];
	}

	/** @return the setup response and the confirm response */
	private String[] enrollTotpResponseWithSecret(MockHttpSession session) throws Exception {
		String setup = this.mvc.perform(post("/api/v1/me/mfa/totp").with(browser()).session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.otpauthUri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/ProjectX:")))
			.andReturn().getResponse().getContentAsString();
		byte[] secret = Base32.decode(JsonPath.read(setup, "$.secret"));
		String confirm = this.mvc
			.perform(post("/api/v1/me/mfa/totp/confirm").with(browser()).session(session)
				.contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + currentCode(secret) + "\"}"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return new String[] { setup, confirm };
	}

	private String currentCode(byte[] secret) {
		return Totp.code(secret, Totp.step(this.clock.instant()));
	}

	private String register() throws Exception {
		String email = uniqueEmail();
		registerRequest(email, PASSWORD).andExpect(status().isAccepted());
		return email;
	}

	private ResultActions registerRequest(String email, String password) throws Exception {
		return this.mvc.perform(post("/api/v1/auth/register").with(browser()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"displayName\":\"Test User\"}"));
	}

	private ResultActions login(MockHttpSession session, String email, String password) throws Exception {
		return this.mvc.perform(post("/api/v1/auth/login").with(browser()).session(session)
			.contentType(MediaType.APPLICATION_JSON).content(loginJson(email, password)));
	}

	private ResultActions mfa(MockHttpSession session, String code) throws Exception {
		return this.mvc.perform(post("/api/v1/auth/mfa").with(browser()).session(session)
			.contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"));
	}

	private void logout(MockHttpSession session) throws Exception {
		this.mvc.perform(post("/api/v1/auth/logout").with(browser()).session(session)).andExpect(status().isNoContent());
	}

	/** A request from this test's own client IP, with a CSRF token. */
	private RequestPostProcessor browser() {
		return browser(this.ip);
	}

	/**
	 * Acts like the frontend running on the given client IP: fetches the XSRF-TOKEN cookie
	 * and echoes it in the X-XSRF-TOKEN header. (Spring Security's {@code csrf()} test
	 * helper is not used because it swaps in a different token repository for the whole
	 * context.) Each test uses its own IP, so tests do not share rate limits.
	 */
	private RequestPostProcessor browser(String clientIp) {
		return (request) -> {
			try {
				Cookie token = this.mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
				request.setCookies(token);
				request.addHeader("X-XSRF-TOKEN", token.getValue());
				request.setRemoteAddr(clientIp);
				return request;
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		};
	}

	private ResultActions loginFrom(String clientIp, String email, String password) throws Exception {
		return this.mvc.perform(post("/api/v1/auth/login").with(browser(clientIp)).session(new MockHttpSession())
			.contentType(MediaType.APPLICATION_JSON).content(loginJson(email, password)));
	}

	static String randomIp() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		return "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
	}

	private List<AuditEventType> eventsFor(String email) {
		long id = this.users.findByEmail(email).orElseThrow().getId();
		return this.auditLog.findByUserIdOrderByOccurredAtAsc(id).stream().map(AuditLogEntry::getEventType).toList();
	}

	private static String loginJson(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}

	private static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

}
