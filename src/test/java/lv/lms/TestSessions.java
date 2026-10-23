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
		return login(registerAdmin());
	}

	/** A new admin account, logged in, with MFA set up and verified in this session. */
	public MockHttpSession adminWithMfa() throws Exception {
		MockHttpSession session = adminWithoutMfa();
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
		return session;
	}

	/** Id of the user logged in to the session, via /api/v1/me. */
	public long userId(MockHttpSession session) throws Exception {
		String me = this.mvc.perform(get("/api/v1/me").session(session)).andReturn().getResponse().getContentAsString();
		return this.users.findByEmail(JsonPath.read(me, "$.email")).orElseThrow().getId();
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

	private String register() throws Exception {
		String email = "user-" + UUID.randomUUID() + "@example.com";
		this.mvc
			.perform(post("/api/v1/auth/register").with(browser()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"displayName\":\"Test\"}"))
			.andExpect(status().isAccepted());
		return email;
	}

	private String registerAdmin() throws Exception {
		String email = register();
		User user = this.users.findByEmail(email).orElseThrow();
		user.grantRole(Role.ADMIN, this.clock.instant());
		this.users.save(user);
		return email;
	}

	private MockHttpSession login(String email) throws Exception {
		MockHttpSession session = new MockHttpSession();
		this.mvc
			.perform(post("/api/v1/auth/login").with(browser()).session(session).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isOk());
		return session;
	}

	private static String randomIp() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		return "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
	}

}
