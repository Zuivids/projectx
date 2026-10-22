package lv.lms.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application settings bound from the {@code app.*} configuration keys.
 *
 * @param name product name shown to users (e.g. as the issuer in authenticator apps)
 * @param mfa MFA settings
 * @param lockout account lockout settings
 * @param rateLimit per-IP request limits for the login endpoints
 */
@Validated
@ConfigurationProperties("app")
public record AppProperties(@NotBlank String name, @Valid @NotNull Mfa mfa, @Valid @NotNull Lockout lockout,
		@Valid @NotNull RateLimit rateLimit) {

	/**
	 * @param encryptionKey base64-encoded 256-bit AES key used to encrypt TOTP secrets
	 * @param pendingTimeout how long a password-verified login may wait for its second factor
	 */
	public record Mfa(@NotBlank String encryptionKey, @NotNull Duration pendingTimeout) {
	}

	/**
	 * Failed password or MFA attempts are counted twice: per account and IP address, and
	 * per account across all addresses.
	 *
	 * @param maxFailedAttemptsPerIp failures from one IP before that IP is blocked for the
	 * account; the attacker locks out only themselves, not the real user
	 * @param maxFailedAttempts failures from all IPs together before the whole account is
	 * locked; protects against guessing spread over many addresses
	 * @param duration how long a block or lock lasts
	 */
	public record Lockout(@Min(1) int maxFailedAttemptsPerIp, @Min(1) int maxFailedAttempts,
			@NotNull Duration duration) {
	}

	/**
	 * @param login limit for {@code POST /api/v1/auth/login}
	 * @param mfa limit for {@code POST /api/v1/auth/mfa}
	 * @param register limit for {@code POST /api/v1/auth/register}
	 */
	public record RateLimit(@Valid @NotNull Rule login, @Valid @NotNull Rule mfa, @Valid @NotNull Rule register) {

		/**
		 * @param limit requests allowed per window (also the burst size)
		 * @param window time in which {@code limit} requests are allowed
		 */
		public record Rule(@Min(1) int limit, @NotNull Duration window) {
		}

	}

}
