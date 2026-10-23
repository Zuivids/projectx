package lv.lms.course;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugsTests {

	@Test
	void removesLatvianDiacritics() {
		assertThat(Slugs.fromTitle("Ātrā latviešu valoda")).isEqualTo("atra-latviesu-valoda");
		assertThat(Slugs.fromTitle("Ķīmija, ģeogrāfija un ļoti īsā žūrija")).isEqualTo("kimija-geografija-un-loti-isa-zurija");
	}

	@Test
	void collapsesPunctuationAndSpaces() {
		assertThat(Slugs.fromTitle("  Java & Spring Boot: 101!  ")).isEqualTo("java-spring-boot-101");
	}

	@Test
	void neverReturnsEmptyAndRespectsMaxLength() {
		assertThat(Slugs.fromTitle("!!!")).isEqualTo("kurss");
		String slug = Slugs.fromTitle("a".repeat(200));
		assertThat(slug).hasSize(Slugs.MAX_LENGTH).matches(Slugs.PATTERN);
	}

}
