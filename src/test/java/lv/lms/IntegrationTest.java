package lv.lms;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/**
 * Full application against a MariaDB container. All integration tests share this setup,
 * so they share one Spring context and one database container.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, IntegrationTest.TestClockConfiguration.class })
public @interface IntegrationTest {

	@TestConfiguration(proxyBeanMethods = false)
	class TestClockConfiguration {

		@Bean
		@Primary
		MutableClock testClock() {
			return new MutableClock(Instant.now().truncatedTo(ChronoUnit.SECONDS));
		}

	}

}
