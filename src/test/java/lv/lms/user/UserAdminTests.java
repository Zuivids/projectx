package lv.lms.user;

import com.jayway.jsonpath.JsonPath;
import lv.lms.IntegrationTest;
import lv.lms.MutableClock;
import lv.lms.TestSessions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class UserAdminTests {

	private static final String USERS = "/api/v1/admin/users/";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserRepository users;

	@Autowired
	private MutableClock clock;

	private TestSessions sessions;

	private MockHttpSession superAdmin;

	private MockHttpSession admin;

	@BeforeEach
	void setUp() throws Exception {
		this.sessions = new TestSessions(this.mvc, this.users, this.clock);
		this.superAdmin = this.sessions.superAdminWithMfa();
		this.admin = this.sessions.adminWithMfa();
	}

	@Test
	void learnersCannotUseUserManagement() throws Exception {
		this.mvc.perform(get("/api/v1/admin/users").session(this.sessions.learner())).andExpect(status().isForbidden());
	}

	@Test
	void searchesByEmailWithoutExposingSecrets() throws Exception {
		String email = this.sessions.register();
		String part = email.substring(5, 17);
		this.mvc.perform(get("/api/v1/admin/users").param("query", part.toUpperCase()).session(this.admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].email").value(hasItem(email)))
			.andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
		// "%" is a plain character, not a wildcard that matches everyone
		this.mvc.perform(get("/api/v1/admin/users").param("query", "%").session(this.admin))
			.andExpect(jsonPath("$.totalItems").value(0));
	}

	@Test
	void blockingEndsTheSessionAndUnblockingAllowsLoginAgain() throws Exception {
		String email = this.sessions.register();
		MockHttpSession learner = this.sessions.login(email);
		long id = this.sessions.userId(email);

		action(post(USERS + id + "/disable"), this.admin).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DISABLED"))
			.andExpect(jsonPath("$.activity[0].type").value("USER_DISABLED"))
			.andExpect(jsonPath("$.activity[0].byAdmin").value(true));
		this.mvc.perform(get("/api/v1/me").session(learner)).andExpect(status().isUnauthorized());
		this.sessions.attemptLogin(new MockHttpSession(), email, TestSessions.PASSWORD)
			.andExpect(status().isUnauthorized());

		action(post(USERS + id + "/enable"), this.admin).andExpect(jsonPath("$.status").value("ACTIVE"));
		this.sessions.login(email);
	}

	@Test
	void nobodyCanBlockThemselves() throws Exception {
		long ownId = this.sessions.userId(this.admin);
		action(post(USERS + ownId + "/disable"), this.admin).andExpect(status().isConflict());
	}

	@Test
	void onlySuperAdminsManageAdminAccounts() throws Exception {
		long otherAdmin = this.sessions.userId(this.sessions.adminWithoutMfa());
		action(post(USERS + otherAdmin + "/disable"), this.admin).andExpect(status().isForbidden());
		action(post(USERS + otherAdmin + "/disable"), this.superAdmin).andExpect(status().isOk());
	}

	@Test
	void rolesAreChangedBySuperAdminsAndTakeEffectImmediately() throws Exception {
		String email = this.sessions.register();
		MockHttpSession learner = this.sessions.login(email);
		long id = this.sessions.userId(email);

		action(put(USERS + id + "/roles/ADMIN"), this.admin).andExpect(status().isForbidden());
		action(put(USERS + id + "/roles/ADMIN"), this.superAdmin).andExpect(status().isOk())
			.andExpect(jsonPath("$.roles").value(hasItem("ADMIN")));
		// The old session had learner rights only; it ends, and the next login has the new role
		this.mvc.perform(get("/api/v1/me").session(learner)).andExpect(status().isUnauthorized());
		MockHttpSession promoted = this.sessions.login(email);
		this.mvc.perform(get("/api/v1/me").session(promoted)).andExpect(jsonPath("$.roles").value(hasItem("ADMIN")));

		action(delete(USERS + id + "/roles/ADMIN"), this.superAdmin).andExpect(status().isOk());
		this.mvc.perform(get("/api/v1/me").session(promoted)).andExpect(status().isUnauthorized());

		action(put(USERS + id + "/roles/LEARNER"), this.superAdmin).andExpect(status().isBadRequest());
		long ownId = this.sessions.userId(this.superAdmin);
		action(delete(USERS + ownId + "/roles/SUPER_ADMIN"), this.superAdmin).andExpect(status().isConflict());
	}

	@Test
	void resettingMfaEndsSessionsAndAllowsPasswordLogin() throws Exception {
		String email = this.sessions.register();
		MockHttpSession learner = this.sessions.login(email);
		this.sessions.enableMfa(learner);
		long id = this.sessions.userId(email);
		this.sessions.attemptLogin(new MockHttpSession(), email, TestSessions.PASSWORD)
			.andExpect(jsonPath("$.status").value("MFA_REQUIRED"));

		action(post(USERS + id + "/reset-mfa"), this.admin).andExpect(status().isOk())
			.andExpect(jsonPath("$.mfaEnabled").value(false))
			.andExpect(jsonPath("$.recoveryCodesLeft").value(0));
		this.mvc.perform(get("/api/v1/me").session(learner)).andExpect(status().isUnauthorized());
		this.sessions.attemptLogin(new MockHttpSession(), email, TestSessions.PASSWORD)
			.andExpect(jsonPath("$.status").value("AUTHENTICATED"));
	}

	@Test
	void unlockLiftsLockoutAndIpBlocks() throws Exception {
		String email = this.sessions.register();
		long id = this.sessions.userId(email);
		for (int i = 0; i < 5; i++) {
			this.sessions.attemptLogin(new MockHttpSession(), email, "wrong password!!");
		}
		this.sessions.attemptLogin(new MockHttpSession(), email, TestSessions.PASSWORD)
			.andExpect(status().isUnauthorized());

		action(post(USERS + id + "/unlock"), this.admin).andExpect(status().isOk());
		this.sessions.login(email);
	}

	@Test
	void unknownUserIsNotFound() throws Exception {
		action(post(USERS + "999999999/disable"), this.admin).andExpect(status().isNotFound());
	}

	@Test
	void userPageShowsOwnAndAdminActivity() throws Exception {
		String email = this.sessions.register();
		this.sessions.login(email);
		long id = this.sessions.userId(email);
		action(post(USERS + id + "/unlock"), this.admin);

		String detail = this.mvc.perform(get(USERS + id).session(this.admin)).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		java.util.List<String> types = JsonPath.read(detail, "$.activity[*].type");
		org.assertj.core.api.Assertions.assertThat(types)
			.containsSubsequence("USER_UNLOCKED", "LOGIN_SUCCEEDED", "USER_REGISTERED");

		// On the admin's own page the same action is marked as done to another account
		long adminId = this.sessions.userId(this.admin);
		this.mvc.perform(get(USERS + adminId).session(this.superAdmin))
			.andExpect(jsonPath("$.activity[0].type").value("USER_UNLOCKED"))
			.andExpect(jsonPath("$.activity[0].byAdmin").value(false))
			.andExpect(jsonPath("$.activity[0].otherUserId").value(id));
	}

	private ResultActions action(MockHttpServletRequestBuilder request, MockHttpSession session) throws Exception {
		return this.mvc.perform(request.with(this.sessions.browser()).session(session));
	}

}
