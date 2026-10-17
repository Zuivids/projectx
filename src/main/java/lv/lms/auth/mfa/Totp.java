package lv.lms.auth.mfa;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.OptionalLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Time-based one-time passwords (RFC 6238) with HMAC-SHA1, 30 second steps and 6 digits:
 * the defaults supported by Google and Microsoft Authenticator.
 */
public final class Totp {

	public static final int PERIOD_SECONDS = 30;

	public static final int DIGITS = 6;

	/** Accept codes one step before or after the current one to allow for clock drift. */
	private static final int ALLOWED_DRIFT_STEPS = 1;

	private Totp() {
	}

	public static long step(Instant time) {
		return Math.floorDiv(time.getEpochSecond(), PERIOD_SECONDS);
	}

	public static String code(byte[] secret, long step) {
		return code(secret, step, DIGITS);
	}

	static String code(byte[] secret, long step, int digits) {
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(secret, "HmacSHA1"));
			byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
			int offset = hash[hash.length - 1] & 0x0F;
			int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16)
					| ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
			int otp = binary % (int) Math.pow(10, digits);
			return String.format("%0" + digits + "d", otp);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA1 is not available", ex);
		}
	}

	/**
	 * Checks a code against the steps around {@code now}.
	 * @return the matching time step, or empty if the code is not valid
	 */
	public static OptionalLong verify(byte[] secret, String code, Instant now) {
		if (code == null || code.length() != DIGITS || !code.chars().allMatch(Character::isDigit)) {
			return OptionalLong.empty();
		}
		byte[] given = code.getBytes(StandardCharsets.US_ASCII);
		long current = step(now);
		for (long step = current - ALLOWED_DRIFT_STEPS; step <= current + ALLOWED_DRIFT_STEPS; step++) {
			byte[] expected = code(secret, step).getBytes(StandardCharsets.US_ASCII);
			if (MessageDigest.isEqual(expected, given)) {
				return OptionalLong.of(step);
			}
		}
		return OptionalLong.empty();
	}

}
