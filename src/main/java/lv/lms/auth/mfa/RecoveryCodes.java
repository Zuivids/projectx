package lv.lms.auth.mfa;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Generates and hashes recovery codes like {@code K7QX2-MP4RA}. The alphabet leaves out
 * look-alike characters (0/O, 1/I). Each code carries 50 random bits, so a fast SHA-256
 * hash is enough; account lockout limits guessing.
 */
final class RecoveryCodes {

	static final int COUNT = 10;

	private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	private static final int LENGTH = 10;

	private static final SecureRandom RANDOM = new SecureRandom();

	private RecoveryCodes() {
	}

	static String generate() {
		StringBuilder code = new StringBuilder(LENGTH + 1);
		for (int i = 0; i < LENGTH; i++) {
			if (i == LENGTH / 2) {
				code.append('-');
			}
			code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
		}
		return code.toString();
	}

	/** Hash of the code with formatting removed, so "k7qx2 mp4ra" matches "K7QX2-MP4RA". */
	static String hash(String code) {
		String normalized = code.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}

}
