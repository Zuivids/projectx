package lv.lms.auth.mfa;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mfa_totp")
public class TotpFactor {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Column(name = "secret_ciphertext", nullable = false)
	private byte[] secretCiphertext;

	@Column(name = "confirmed_at")
	private Instant confirmedAt;

	@Column(name = "last_used_step")
	private Long lastUsedStep;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected TotpFactor() {
	}

	TotpFactor(Long userId, byte[] secretCiphertext, Instant now) {
		this.userId = userId;
		this.secretCiphertext = secretCiphertext;
		this.createdAt = now;
	}

	boolean isConfirmed() {
		return this.confirmedAt != null;
	}

	void replaceSecret(byte[] secretCiphertext, Instant now) {
		this.secretCiphertext = secretCiphertext;
		this.createdAt = now;
		this.lastUsedStep = null;
	}

	void confirm(long step, Instant now) {
		this.confirmedAt = now;
		this.lastUsedStep = step;
	}

	/**
	 * Marks a time step as used. Each code works once: a step at or before the last used
	 * one is rejected, which blocks replay of an intercepted code.
	 * @return {@code false} if the step was already used
	 */
	boolean useStep(long step) {
		if (this.lastUsedStep != null && step <= this.lastUsedStep) {
			return false;
		}
		this.lastUsedStep = step;
		return true;
	}

	Long getUserId() {
		return this.userId;
	}

	byte[] getSecretCiphertext() {
		return this.secretCiphertext;
	}

}
