package lv.lms.user.admin;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import lv.lms.audit.Actor;
import lv.lms.audit.AuditEventType;
import lv.lms.audit.AuditLogEntry;
import lv.lms.audit.AuditLogRepository;
import lv.lms.audit.AuditService;
import lv.lms.auth.LoginAttemptTracker;
import lv.lms.auth.mfa.MfaService;
import lv.lms.common.ApiException;
import lv.lms.common.PageResponse;
import lv.lms.user.Role;
import lv.lms.user.User;
import lv.lms.user.UserRepository;
import lv.lms.user.UserStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User management for admins (W-08). Rules:
 * <ul>
 * <li>nobody can block themselves, remove their own roles or reset their own MFA here;</li>
 * <li>accounts with an admin role, and all role changes, need a super admin;</li>
 * <li>blocking, role changes and MFA resets end the user's open sessions at once;</li>
 * <li>every action is audited with the admin as actor and the user as target.</li>
 * </ul>
 */
@Service
public class UserAdminService {

	private static final int MAX_PAGE_SIZE = 100;

	private static final int ACTIVITY_SHOWN = 30;

	private static final Set<Role> ASSIGNABLE_ROLES = Set.of(Role.ADMIN, Role.SUPER_ADMIN);

	private final UserRepository users;

	private final MfaService mfa;

	private final LoginAttemptTracker attempts;

	private final AuditService audit;

	private final AuditLogRepository auditLog;

	private final Clock clock;

	UserAdminService(UserRepository users, MfaService mfa, LoginAttemptTracker attempts, AuditService audit,
			AuditLogRepository auditLog, Clock clock) {
		this.users = users;
		this.mfa = mfa;
		this.attempts = attempts;
		this.audit = audit;
		this.auditLog = auditLog;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResponse<UserAdminResponses.UserSummary> list(String query, UserStatus status, int page, int size) {
		String pattern = (query == null || query.isBlank()) ? null
				: "%" + escapeLike(query.strip().toLowerCase(Locale.ROOT)) + "%";
		Page<User> result = this.users.search(pattern, status, PageRequest.of(Math.max(page, 0),
				Math.clamp(size, 1, MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "createdAt")));
		Set<Long> withMfa = this.mfa.enabledAmong(result.getContent().stream().map(User::getId).toList());
		Instant now = this.clock.instant();
		return PageResponse.of(result, (u) -> new UserAdminResponses.UserSummary(u.getId(), u.getEmail(),
				u.getDisplayName(), sortedRoles(u), u.getStatus(), withMfa.contains(u.getId()), u.isLocked(now)));
	}

	@Transactional(readOnly = true)
	public UserAdminResponses.UserDetail get(long userId) {
		return toDetail(find(userId));
	}

	@Transactional
	public UserAdminResponses.UserDetail disable(long userId, Actor actor) {
		User user = find(userId);
		requireNotSelf(user, actor, "You cannot block your own account");
		requireManageable(user, actor);
		if (user.getStatus() != UserStatus.DISABLED) {
			user.disable(now());
			this.audit.recordAdminAction(AuditEventType.USER_DISABLED, actor, userId, null);
		}
		return toDetail(user);
	}

	@Transactional
	public UserAdminResponses.UserDetail enable(long userId, Actor actor) {
		User user = findManageable(userId, actor);
		if (user.getStatus() != UserStatus.ACTIVE) {
			user.enable(now());
			this.audit.recordAdminAction(AuditEventType.USER_ENABLED, actor, userId, null);
		}
		return toDetail(user);
	}

	/** Lifts a lockout after failed logins, on the account and on every IP address. */
	@Transactional
	public UserAdminResponses.UserDetail unlock(long userId, Actor actor) {
		User user = findManageable(userId, actor);
		user.resetFailedAttempts(now());
		this.attempts.resetAll(userId);
		this.audit.recordAdminAction(AuditEventType.USER_UNLOCKED, actor, userId, null);
		return toDetail(user);
	}

	/** For a user who lost their authenticator: they can log in with the password and set it up again. */
	@Transactional
	public UserAdminResponses.UserDetail resetMfa(long userId, Actor actor) {
		User user = find(userId);
		requireNotSelf(user, actor, "Change your own MFA on the security page");
		requireManageable(user, actor);
		if (this.mfa.removeAll(userId)) {
			// Sessions that passed MFA with the old authenticator must not continue
			user.endSessions(now());
			this.audit.recordAdminAction(AuditEventType.MFA_RESET_BY_ADMIN, actor, userId, null);
		}
		return toDetail(user);
	}

	@Transactional
	public UserAdminResponses.UserDetail grantRole(long userId, Role role, Actor actor) {
		requireSuperAdmin(actor, "Only a super admin can change roles");
		requireAssignable(role);
		User user = find(userId);
		requireNotSelf(user, actor, "You cannot change your own roles");
		if (!user.hasRole(role)) {
			user.grantRole(role, now());
			this.audit.recordAdminAction(AuditEventType.ROLE_GRANTED, actor, userId, role.name());
		}
		return toDetail(user);
	}

	@Transactional
	public UserAdminResponses.UserDetail revokeRole(long userId, Role role, Actor actor) {
		requireSuperAdmin(actor, "Only a super admin can change roles");
		requireAssignable(role);
		User user = find(userId);
		requireNotSelf(user, actor, "You cannot change your own roles");
		if (user.hasRole(role)) {
			user.revokeRole(role, now());
			this.audit.recordAdminAction(AuditEventType.ROLE_REVOKED, actor, userId, role.name());
		}
		return toDetail(user);
	}

	/** Admin accounts can only be managed by a super admin. */
	private User findManageable(long userId, Actor actor) {
		User user = find(userId);
		requireManageable(user, actor);
		return user;
	}

	private void requireManageable(User user, Actor actor) {
		if (user.isStaff()) {
			requireSuperAdmin(actor, "Only a super admin can manage admin accounts");
		}
	}

	private void requireSuperAdmin(Actor actor, String message) {
		// Checked against the database, not the session, so a revoked role counts at once
		if (!find(actor.userId()).hasRole(Role.SUPER_ADMIN)) {
			throw new ApiException(HttpStatus.FORBIDDEN, message);
		}
	}

	private static void requireNotSelf(User user, Actor actor, String message) {
		if (user.getId() == actor.userId()) {
			throw ApiException.conflict(message);
		}
	}

	private static void requireAssignable(Role role) {
		if (!ASSIGNABLE_ROLES.contains(role)) {
			throw ApiException.badRequest("Only ADMIN and SUPER_ADMIN can be granted or removed");
		}
	}

	private User find(long userId) {
		return this.users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
	}

	private UserAdminResponses.UserDetail toDetail(User user) {
		Instant now = now();
		List<UserAdminResponses.Activity> activity = this.auditLog
			.findActivity(user.getId(), PageRequest.of(0, ACTIVITY_SHOWN))
			.stream()
			.map((e) -> toActivity(e, user.getId()))
			.toList();
		return new UserAdminResponses.UserDetail(user.getId(), user.getEmail(), user.getDisplayName(),
				sortedRoles(user), user.getStatus(), this.mfa.isEnabled(user.getId()),
				this.mfa.remainingRecoveryCodes(user.getId()), user.isLocked(now) ? user.getLockedUntil() : null,
				user.getCreatedAt(), activity);
	}

	private static UserAdminResponses.Activity toActivity(AuditLogEntry entry, long userId) {
		Long target = entry.getTargetUserId();
		boolean byAdmin = target != null && target == userId;
		// This user's own admin actions on other accounts must not look like events on this account
		Long otherUserId = (target != null && target != userId) ? target : null;
		return new UserAdminResponses.Activity(entry.getOccurredAt(), entry.getEventType(), entry.getIpAddress(),
				entry.getDetails(), byAdmin, otherUserId);
	}

	private static List<Role> sortedRoles(User user) {
		return user.getRoles().stream().sorted().toList();
	}

	/** Treats % and _ typed by the admin as plain characters in the LIKE search. */
	private static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private Instant now() {
		return this.clock.instant();
	}

}
