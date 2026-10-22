package lv.lms.auth.mfa;

import java.time.Duration;

import lv.lms.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class SecretCipherTests {

	private final SecretCipher cipher = new SecretCipher(properties("laTfKL0uYDqlzCauRBc8fjb1EchhryHDMaG/WAmO9fA="));

	@Test
	void roundTrips() {
		byte[] secret = "a 20 byte secret!!!!".getBytes();
		assertThat(this.cipher.decrypt(this.cipher.encrypt(secret, 42), 42)).isEqualTo(secret);
	}

	@Test
	void usesFreshIvForEveryEncryption() {
		byte[] secret = "a 20 byte secret!!!!".getBytes();
		assertThat(this.cipher.encrypt(secret, 42)).isNotEqualTo(this.cipher.encrypt(secret, 42));
	}

	@Test
	void ciphertextIsBoundToUser() {
		byte[] encrypted = this.cipher.encrypt("a 20 byte secret!!!!".getBytes(), 42);
		assertThatIllegalStateException().isThrownBy(() -> this.cipher.decrypt(encrypted, 43));
	}

	@Test
	void rejectsShortKey() {
		assertThatIllegalStateException().isThrownBy(() -> new SecretCipher(properties("c2hvcnQ=")));
	}

	private static AppProperties properties(String key) {
		AppProperties.RateLimit.Rule rule = new AppProperties.RateLimit.Rule(20, Duration.ofMinutes(1));
		return new AppProperties("Test", new AppProperties.Mfa(key, Duration.ofMinutes(5)),
				new AppProperties.Lockout(5, 20, Duration.ofMinutes(15)), new AppProperties.RateLimit(rule, rule, rule));
	}

}
