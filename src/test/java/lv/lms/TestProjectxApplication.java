package lv.lms;

import org.springframework.boot.SpringApplication;

public class TestProjectxApplication {

	public static void main(String[] args) {
		SpringApplication.from(ProjectxApplication::main)
			.with(TestcontainersConfiguration.class)
			.withAdditionalProfiles("local")
			.run(args);
	}

}
