package lv.lms.user.admin;

import java.time.Instant;
import java.util.List;

import lv.lms.audit.AuditEventType;
import lv.lms.user.Role;
import lv.lms.user.UserStatus;

/** Response DTOs of the admin user API. Never contain password hashes or MFA secrets. */
public final class UserAdminResponses {

	private UserAdminResponses() {
	}

	public record UserSummary(long id, String email, String displayName, List<Role> roles, UserStatus status,
			boolean mfaEnabled, boolean locked) {
	}

	/**
	 * @param lockedUntil set while the account is locked after too many failed logins
	 * @param activity the user's own security events and admin actions on the account,
	 * newest first
	 */
	public record UserDetail(long id, String email, String displayName, List<Role> roles, UserStatus status,
			boolean mfaEnabled, long recoveryCodesLeft, Instant lockedUntil, Instant createdAt,
			List<Activity> activity) {
	}

	/**
	 * @param byAdmin the event was an admin's action on this account
	 * @param otherUserId set when this user (as an admin) acted on another account: that
	 * account's id
	 */
	public record Activity(Instant occurredAt, AuditEventType type, String ipAddress, String details,
			boolean byAdmin, Long otherUserId) {
	}

}
