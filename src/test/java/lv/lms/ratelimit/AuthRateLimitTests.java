package lv.lms.ratelimit;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import lv.lms.IntegrationTest;
import lv.lms.MutableClock;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rate limits run before Spring Security, so these requests need no CSRF token: a request
 * over the limit is refused before the CSRF check (403) would even happen.
 */
@IntegrationTest
class AuthRateLimitTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private MutableClock clock;

	private final String ip = randomIp();

	@Test
	void loginIsLimitedPerIp() throws Exception {
		// Default: 20 per minute. Without a CSRF token these are 403, but they still count.
		for (int i = 0; i < 20; i++) {
			postFrom(this.ip, "/api/v1/auth/login").andExpect(status().isForbidden());
		}
		postFrom(this.ip, "/api/v1/auth/login").andExpect(status().isTooManyRequests())
			.andExpect(header().longValue("Retry-After", 3))
			.andExpect(jsonPath("$.status").value(429))
			.andExpect(jsonPath("$.detail").value("Too many attempts, please try again later"));

		// Other clients are not affected
		postFrom(randomIp(), "/api/v1/auth/login").andExpect(status().isForbidden());

		// Tokens come back over time
		this.clock.advance(Duration.ofSeconds(3));
		postFrom(this.ip, "/api/v1/auth/login").andExpect(status().isForbidden());
	}

	@Test
	void registrationHasItsOwnStricterLimit() throws Exception {
		for (int i = 0; i < 10; i++) {
			postFrom(this.ip, "/api/v1/auth/register").andExpect(status().isForbidden());
		}
		postFrom(this.ip, "/api/v1/auth/register").andExpect(status().isTooManyRequests())
			.andExpect(header().longValue("Retry-After", 360));
		// The login limit is separate
		postFrom(this.ip, "/api/v1/auth/login").andExpect(status().isForbidden());
	}

	@Test
	void encodedPathCannotBypassTheLimit() throws Exception {
		for (int i = 0; i < 20; i++) {
			postFrom(this.ip, "/api/v1/auth/login");
		}
		// "%6C" is an encoded "l"; Spring would route this to the login endpoint
		postFrom(this.ip, "/api/v1/auth/%6Cogin").andExpect(status().isTooManyRequests());
	}

	@Test
	void otherEndpointsAreNotLimited() throws Exception {
		for (int i = 0; i < 25; i++) {
			postFrom(this.ip, "/api/v1/auth/logout").andExpect(status().isForbidden());
		}
	}

	private ResultActions postFrom(String clientIp, String path) throws Exception {
		// URI.create keeps the path exactly as written (no re-encoding of "%")
		return this.mvc.perform(post(URI.create(path)).contentType(MediaType.APPLICATION_JSON).content("{}").with((request) -> {
			request.setRemoteAddr(clientIp);
			return request;
		}));
	}

	private static String randomIp() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		return "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
	}

}
