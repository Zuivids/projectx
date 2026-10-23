package lv.lms.course;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import lv.lms.IntegrationTest;
import lv.lms.MutableClock;
import lv.lms.TestSessions;
import lv.lms.audit.AuditEventType;
import lv.lms.audit.AuditLogEntry;
import lv.lms.audit.AuditLogRepository;
import lv.lms.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class CourseAdminTests {

	private static final String COURSES = "/api/v1/admin/courses";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserRepository users;

	@Autowired
	private MutableClock clock;

	@Autowired
	private AuditLogRepository auditLog;

	private TestSessions sessions;

	private MockHttpSession admin;

	@BeforeEach
	void setUp() throws Exception {
		this.sessions = new TestSessions(this.mvc, this.users, this.clock);
		this.admin = this.sessions.adminWithMfa();
	}

	@Test
	void onlyAdminsWithMfaCanManageCourses() throws Exception {
		this.mvc.perform(get(COURSES)).andExpect(status().isUnauthorized());
		this.mvc.perform(get(COURSES).session(this.sessions.learner())).andExpect(status().isForbidden());
		this.mvc.perform(get(COURSES).session(this.sessions.adminWithoutMfa())).andExpect(status().isForbidden());
		this.mvc.perform(get(COURSES).session(this.admin)).andExpect(status().isOk());
	}

	@Test
	void generatesUniqueSlugsFromLatvianTitles() throws Exception {
		String title = "Ātrā latviešu valoda " + UUID.randomUUID().toString().substring(0, 8);
		String expected = Slugs.fromTitle(title);
		createCourse(title, null).andExpect(status().isCreated()).andExpect(jsonPath("$.slug").value(expected));
		createCourse(title, null).andExpect(status().isCreated()).andExpect(jsonPath("$.slug").value(expected + "-2"));
		// An explicitly chosen slug that is taken is refused instead of changed
		createCourse("Cits kurss", expected).andExpect(status().isConflict());
	}

	@Test
	void rejectsInvalidCourses() throws Exception {
		send(post(COURSES), "{\"title\":\"\",\"description\":\"\",\"priceCents\":100}").andExpect(status().isBadRequest());
		send(post(COURSES), "{\"title\":\"A\",\"description\":\"\",\"priceCents\":-1}").andExpect(status().isBadRequest());
		send(post(COURSES), "{\"title\":\"A\",\"slug\":\"Ar Atstarpēm\",\"description\":\"\",\"priceCents\":1}")
			.andExpect(status().isBadRequest());
	}

	@Test
	void buildPublishAndShowInCatalog() throws Exception {
		long id = newCourseId();
		String slug = slugOf(id);

		// Cannot publish an empty course
		send(post(COURSES + "/" + id + "/publish"), null).andExpect(status().isConflict());

		long sectionId = JsonPath.<Integer>read(
				send(post(COURSES + "/" + id + "/sections"), "{\"title\":\"Ievads\"}").andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString(),
				"$.sections[0].id")
			.longValue();
		String afterLessons = addLesson(id, sectionId, "Pirmā nodarbība", "AUDIO", true, "Bezmaksas ieskats");
		afterLessons = addLesson(id, sectionId, "Otrā nodarbība", "TEXT", false, "Maksas saturs");
		List<Integer> lessonIds = JsonPath.read(afterLessons, "$.sections[0].lessons[*].id");

		// Reorder: second lesson first
		send(put(COURSES + "/" + id + "/sections/" + sectionId + "/lessons/order"),
				"{\"ids\":[" + lessonIds.get(1) + "," + lessonIds.get(0) + "]}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sections[0].lessons[0].title").value("Otrā nodarbība"))
			.andExpect(jsonPath("$.sections[0].lessons[0].position").value(0));

		// Not in the catalog while it is a draft
		this.mvc.perform(get("/api/v1/courses/" + slug)).andExpect(status().isNotFound());

		send(post(COURSES + "/" + id + "/publish"), null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.canBeDeleted").value(false));

		// Public catalog, no login needed
		this.mvc.perform(get("/api/v1/courses?size=50")).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].slug").value(hasItem(slug)));
		this.mvc.perform(get("/api/v1/courses/" + slug)).andExpect(status().isOk())
			.andExpect(jsonPath("$.lessonCount").value(2))
			.andExpect(jsonPath("$.totalDurationSeconds").value(600))
			.andExpect(jsonPath("$.sections[0].lessons[1].preview").value(true))
			// The outline never contains lesson content
			.andExpect(jsonPath("$.sections[0].lessons[0].contentText").doesNotExist());
		this.mvc.perform(get("/api/v1/courses/" + slug + "/lessons/" + lessonIds.get(0))).andExpect(status().isOk())
			.andExpect(jsonPath("$.contentText").value("Bezmaksas ieskats"));
		this.mvc.perform(get("/api/v1/courses/" + slug + "/lessons/" + lessonIds.get(1)))
			.andExpect(status().isForbidden());

		// A published course must keep at least one lesson
		send(delete(COURSES + "/" + id + "/lessons/" + lessonIds.get(0)), null).andExpect(status().isOk());
		send(delete(COURSES + "/" + id + "/lessons/" + lessonIds.get(1)), null).andExpect(status().isConflict());

		assertThat(eventsFor(this.admin)).contains(AuditEventType.COURSE_CREATED,
				AuditEventType.COURSE_CONTENT_CHANGED, AuditEventType.COURSE_PUBLISHED);
	}

	@Test
	void archiveRestoreAndDelete() throws Exception {
		long id = newCourseId();
		String slug = slugOf(id);
		send(post(COURSES + "/" + id + "/sections"), "{\"title\":\"S\"}");
		long sectionId = sectionIds(id).get(0);
		addLesson(id, sectionId, "L", "TEXT", false, "x");
		send(post(COURSES + "/" + id + "/publish"), null).andExpect(status().isOk());

		send(post(COURSES + "/" + id + "/archive"), null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ARCHIVED"));
		this.mvc.perform(get("/api/v1/courses?size=50"))
			.andExpect(jsonPath("$.items[*].slug").value(not(hasItem(slug))));
		this.mvc.perform(get("/api/v1/courses/" + slug)).andExpect(status().isNotFound());

		send(post(COURSES + "/" + id + "/restore"), null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"));
		// Was published once, so it can only be archived, not deleted
		send(delete(COURSES + "/" + id), null).andExpect(status().isConflict());

		long fresh = newCourseId();
		send(delete(COURSES + "/" + fresh), null).andExpect(status().isNoContent());
		this.mvc.perform(get(COURSES + "/" + fresh).session(this.admin)).andExpect(status().isNotFound());
	}

	@Test
	void refusesUpdateBasedOnAnOldVersion() throws Exception {
		long id = newCourseId();
		String course = this.mvc.perform(get(COURSES + "/" + id).session(this.admin))
			.andReturn().getResponse().getContentAsString();
		int version = JsonPath.read(course, "$.version");
		String slug = JsonPath.read(course, "$.slug");

		send(put(COURSES + "/" + id), updateJson("Jauns nosaukums", slug, version)).andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Jauns nosaukums"))
			.andExpect(jsonPath("$.version").value(version + 1));
		// Second editor still has the old version
		send(put(COURSES + "/" + id), updateJson("Cits nosaukums", slug, version)).andExpect(status().isConflict());
	}

	@Test
	void sectionsAndLessonsMustBelongToTheCourse() throws Exception {
		long first = newCourseId();
		long second = newCourseId();
		send(post(COURSES + "/" + first + "/sections"), "{\"title\":\"A\"}");
		send(post(COURSES + "/" + first + "/sections"), "{\"title\":\"B\"}");
		List<Long> sections = sectionIds(first);

		// A section of the first course cannot be changed through the second course
		send(put(COURSES + "/" + second + "/sections/" + sections.get(0)), "{\"title\":\"X\"}")
			.andExpect(status().isNotFound());
		// The new order must list every section exactly once
		send(put(COURSES + "/" + first + "/sections/order"), "{\"ids\":[" + sections.get(0) + "]}")
			.andExpect(status().isBadRequest());
		send(put(COURSES + "/" + first + "/sections/order"),
				"{\"ids\":[" + sections.get(1) + "," + sections.get(0) + "]}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sections[0].title").value("B"));
	}

	private String addLesson(long courseId, long sectionId, String title, String type, boolean preview,
			String text) throws Exception {
		String json = "{\"title\":\"" + title + "\",\"type\":\"" + type + "\",\"contentText\":\"" + text
				+ "\",\"durationSeconds\":300,\"preview\":" + preview + "}";
		return send(post(COURSES + "/" + courseId + "/sections/" + sectionId + "/lessons"), json)
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
	}

	private long newCourseId() throws Exception {
		String body = createCourse("Kurss " + UUID.randomUUID(), null).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.<Integer>read(body, "$.id").longValue();
	}

	private String slugOf(long courseId) throws Exception {
		return JsonPath.read(this.mvc.perform(get(COURSES + "/" + courseId).session(this.admin))
			.andReturn().getResponse().getContentAsString(), "$.slug");
	}

	private List<Long> sectionIds(long courseId) throws Exception {
		List<Integer> ids = JsonPath.read(this.mvc.perform(get(COURSES + "/" + courseId).session(this.admin))
			.andReturn().getResponse().getContentAsString(), "$.sections[*].id");
		return ids.stream().map(Integer::longValue).toList();
	}

	private ResultActions createCourse(String title, String slug) throws Exception {
		String slugField = (slug != null) ? ",\"slug\":\"" + slug + "\"" : "";
		return send(post(COURSES), "{\"title\":\"" + title + "\"" + slugField
				+ ",\"description\":\"Apraksts\",\"priceCents\":4900,\"level\":\"BEGINNER\"}");
	}

	private static String updateJson(String title, String slug, int version) {
		return "{\"title\":\"" + title + "\",\"slug\":\"" + slug
				+ "\",\"description\":\"Apraksts\",\"priceCents\":5900,\"version\":" + version + "}";
	}

	private ResultActions send(
			org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String json)
			throws Exception {
		request.with(this.sessions.browser()).session(this.admin);
		if (json != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		return this.mvc.perform(request);
	}

	private List<AuditEventType> eventsFor(MockHttpSession session) throws Exception {
		return this.auditLog.findByUserIdOrderByOccurredAtAsc(this.sessions.userId(session))
			.stream()
			.map(AuditLogEntry::getEventType)
			.toList();
	}

}
