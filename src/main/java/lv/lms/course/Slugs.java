package lv.lms.course;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** URL-friendly names such as {@code atra-latviesu-valoda} for "Ātrā latviešu valoda". */
public final class Slugs {

	public static final int MAX_LENGTH = 120;

	/** Lowercase letters and digits, single hyphens between words. */
	public static final String PATTERN = "^[a-z0-9]+(-[a-z0-9]+)*$";

	private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

	private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

	private Slugs() {
	}

	public static String fromTitle(String title) {
		// NFD splits "ā" into "a" + a combining mark, which is then removed
		String ascii = DIACRITICS.matcher(Normalizer.normalize(title, Normalizer.Form.NFD)).replaceAll("");
		String slug = NON_ALPHANUMERIC.matcher(ascii.toLowerCase(Locale.ROOT)).replaceAll("-");
		slug = trimHyphens(slug);
		if (slug.length() > MAX_LENGTH) {
			slug = trimHyphens(slug.substring(0, MAX_LENGTH));
		}
		return slug.isEmpty() ? "kurss" : slug;
	}

	private static String trimHyphens(String value) {
		int start = 0;
		int end = value.length();
		while (start < end && value.charAt(start) == '-') {
			start++;
		}
		while (end > start && value.charAt(end - 1) == '-') {
			end--;
		}
		return value.substring(start, end);
	}

}
