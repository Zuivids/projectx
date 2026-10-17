package lv.lms.auth.mfa;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTests {

	/** SHA-1 seed from RFC 6238 Appendix B. */
	private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

	@Test
	void matchesRfc6238TestVectors() {
		assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(59)), 8)).isEqualTo("94287082");
		assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1111111109)), 8)).isEqualTo("07081804");
		assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1234567890)), 8)).isEqualTo("89005924");
		assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(2000000000)), 8)).isEqualTo("69279037");
	}

	@Test
	void acceptsOneStepOfClockDriftOnly() {
		Instant now = Instant.ofEpochSecond(1_800_000_000L);
		long step = Totp.step(now);
		assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now)).hasValue(step - 1);
		assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 1), now)).hasValue(step + 1);
		assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 2), now)).isEmpty();
		assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 2), now)).isEmpty();
	}

	@Test
	void rejectsMalformedCodes() {
		Instant now = Instant.ofEpochSecond(1_800_000_000L);
		assertThat(Totp.verify(RFC_SECRET, null, now)).isEmpty();
		assertThat(Totp.verify(RFC_SECRET, "12345", now)).isEmpty();
		assertThat(Totp.verify(RFC_SECRET, "12a456", now)).isEmpty();
	}

	@Test
	void base32MatchesRfc4648AndRoundTrips() {
		assertThat(Base32.encode(RFC_SECRET)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
		assertThat(Base32.decode("gezdgnbvgy3tqojqgezdgnbvgy3tqojq")).isEqualTo(RFC_SECRET);
		byte[] odd = { 1, 2, 3, 4, 5, 6, 7 };
		assertThat(Base32.decode(Base32.encode(odd))).isEqualTo(odd);
	}

}
