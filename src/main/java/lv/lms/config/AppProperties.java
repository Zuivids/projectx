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
 */
@Validated
@ConfigurationProperties("app")
public record AppProperties(@NotBlank String name, @Valid @NotNull Mfa mfa, @Valid @NotNull Lockout lockout) {

	/**
	 * @param encryptionKey base64-encoded 256-bit AES key used to encrypt TOTP secrets
	 * @param pendingTimeout how long a password-verified login may wait for its second factor
	 */
	public record Mfa(@NotBlank String encryptionKey, @NotNull Duration pendingTimeout) {
	}

	/**
	 * @param maxFailedAttempts failed password or MFA attempts before the account is locked
	 * @param duration how long the account stays locked
	 */
	public record Lockout(@Min(1) int maxFailedAttempts, @NotNull Duration duration) {
	}

}
