package lv.lms.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

	/** Single time source, so tests can control time. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
