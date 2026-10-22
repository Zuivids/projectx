package lv.lms.user;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "app_user")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 320)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "display_name", nullable = false, length = 100)
	private String displayName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserStatus status;

	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

	@Column(name = "failed_login_count", nullable = false)
	private int failedLoginCount;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 30)
	private Set<Role> roles = EnumSet.noneOf(Role.class);

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected User() {
	}

	public User(String email, String passwordHash, String displayName, Role role, Instant now) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.status = UserStatus.ACTIVE;
		this.roles.add(role);
		this.createdAt = now;
		this.updatedAt = now;
	}

	public boolean isLocked(Instant now) {
		return this.lockedUntil != null && this.lockedUntil.isAfter(now);
	}

	public boolean canLogIn(Instant now) {
		return this.status == UserStatus.ACTIVE && !isLocked(now);
	}

	public boolean requiresMfa() {
		return this.roles.stream().anyMatch(Role::requiresMfa);
	}

	/**
	 * Records a failed password or MFA attempt and locks the account once the limit is
	 * reached.
	 * @return {@code true} if this failure locked the account
	 */
	public boolean registerFailedAttempt(int maxAttempts, Duration lockDuration, Instant now) {
		this.failedLoginCount++;
		this.updatedAt = now;
		if (this.failedLoginCount >= maxAttempts) {
			this.lockedUntil = now.plus(lockDuration);
			this.failedLoginCount = 0;
			return true;
		}
		return false;
	}

	public void resetFailedAttempts(Instant now) {
		if (this.failedLoginCount != 0 || this.lockedUntil != null) {
			this.failedLoginCount = 0;
			this.lockedUntil = null;
			this.updatedAt = now;
		}
	}

	public void changePasswordHash(String passwordHash, Instant now) {
		this.passwordHash = passwordHash;
		this.updatedAt = now;
	}

	public void grantRole(Role role, Instant now) {
		if (this.roles.add(role)) {
			this.updatedAt = now;
		}
	}

	public Long getId() {
		return this.id;
	}

	public String getEmail() {
		return this.email;
	}

	public String getPasswordHash() {
		return this.passwordHash;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public UserStatus getStatus() {
		return this.status;
	}

	public Instant getEmailVerifiedAt() {
		return this.emailVerifiedAt;
	}

	public Set<Role> getRoles() {
		return Set.copyOf(this.roles);
	}

}
