package lv.lms.auth.mfa;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mfa_recovery_code")
public class RecoveryCode {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "code_hash", nullable = false, length = 64)
	private String codeHash;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected RecoveryCode() {
	}

	RecoveryCode(Long userId, String codeHash, Instant now) {
		this.userId = userId;
		this.codeHash = codeHash;
		this.createdAt = now;
	}

}
