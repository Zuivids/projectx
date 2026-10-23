package lv.lms;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import lv.lms.auth.mfa.Base32;
import lv.lms.auth.mfa.Totp;
import lv.lms.user.Role;
import lv.lms.user.User;
import lv.lms.user.UserRepository;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Creates logged-in sessions for integration tests, going through the real API like the
 * frontend does. Each instance uses its own client IP, so tests do not share rate limits.
 */
public class TestSessions {

	public static final String PASSWORD = "correct horse battery";

	private final MockMvc mvc;

	private final UserRepository users;

	private final MutableClock clock;

	private final String ip = randomIp();

	public TestSessions(MockMvc mvc, UserRepository users, MutableClock clock) {
		this.mvc = mvc;
		this.users = users;
		this.clock = clock;
	}

	/** A new learner account, logged in. */
	public MockHttpSession learner() throws Exception {
		return login(register());
	}

	/** A new admin account, logged in, without MFA in this session. */
	public MockHttpSession adminWithoutMfa() throws Exception {
		return login(registerWithRole(Role.ADMIN));
	}

	/** A new admin account, logged in, with MFA set up and verified in this session. */
	public MockHttpSession adminWithMfa() throws Exception {
		MockHttpSession session = adminWithoutMfa();
		enableMfa(session);
		return session;
	}

	/** A new super admin account, logged in, with MFA set up and verified in this session. */
	public MockHttpSession superAdminWithMfa() throws Exception {
		MockHttpSession session = login(registerWithRole(Role.SUPER_ADMIN));
		enableMfa(session);
		return session;
	}

	/** Registers a new learner account and returns its email. */
	public String register() throws Exception {
		String email = "user-" + UUID.randomUUID() + "@example.com";
		this.mvc
			.perform(post("/api/v1/auth/register").with(browser()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"displayName\":\"Test\"}"))
			.andExpect(status().isAccepted());
		return email;
	}

	/** Logs in with the test password; expects a session without an MFA step. */
	public MockHttpSession login(String email) throws Exception {
		MockHttpSession session = new MockHttpSession();
		attemptLogin(session, email, PASSWORD).andExpect(status().isOk());
		return session;
	}

	/** A login request whose outcome the test checks itself. */
	public ResultActions attemptLogin(MockHttpSession session, String email, String password) throws Exception {
		return this.mvc.perform(post("/api/v1/auth/login").with(browser()).session(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
	}

	/** Sets up and confirms MFA in the session; the session becomes MFA-verified. */
	public byte[] enableMfa(MockHttpSession session) throws Exception {
		String setup = this.mvc.perform(post("/api/v1/me/mfa/totp").with(browser()).session(session))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		byte[] secret = Base32.decode(JsonPath.read(setup, "$.secret"));
		String code = Totp.code(secret, Totp.step(this.clock.instant()));
		this.mvc
			.perform(post("/api/v1/me/mfa/totp/confirm").with(browser()).session(session)
				.contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
			.andExpect(status().isOk());
		return secret;
	}

	/** Id of the user logged in to the session, via /api/v1/me. */
	public long userId(MockHttpSession session) throws Exception {
		String me = this.mvc.perform(get("/api/v1/me").session(session)).andReturn().getResponse().getContentAsString();
		return userId(JsonPath.<String>read(me, "$.email"));
	}

	public long userId(String email) {
		return this.users.findByEmail(email).orElseThrow().getId();
	}

	/** Adds a CSRF token the way the frontend does, sent from this instance's client IP. */
	public RequestPostProcessor browser() {
		return (request) -> {
			try {
				Cookie token = this.mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
				request.setCookies(token);
				request.addHeader("X-XSRF-TOKEN", token.getValue());
				request.setRemoteAddr(this.ip);
				return request;
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		};
	}

	private String registerWithRole(Role role) throws Exception {
		String email = register();
		User user = this.users.findByEmail(email).orElseThrow();
		user.grantRole(role, this.clock.instant());
		this.users.save(user);
		return email;
	}

	private static String randomIp() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		return "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
	}

}
