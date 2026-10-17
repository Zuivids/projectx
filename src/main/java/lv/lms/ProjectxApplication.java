package lv.lms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// The in-memory default user is excluded: authentication is handled by lv.lms.auth.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class ProjectxApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProjectxApplication.class, args);
	}

}
