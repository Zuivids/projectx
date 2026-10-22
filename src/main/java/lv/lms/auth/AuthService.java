package lv.lms.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import lv.lms.audit.AuditEventType;
import lv.lms.audit.AuditService;
import lv.lms.auth.mfa.MfaService;
import lv.lms.config.AppProperties;
import lv.lms.user.Role;
import lv.lms.user.User;
import lv.lms.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final MfaService mfa;

	private final AuditService audit;

	private final AppProperties properties;

	private final Clock clock;

	/** Compared against when the email is unknown, so the response time does not reveal it. */
	private final String dummyPasswordHash;

	AuthService(UserRepository users, PasswordEncoder passwordEncoder, MfaService mfa, AuditService audit,
			AppProperties properties, Clock clock) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.mfa = mfa;
		this.audit = audit;
		this.properties = properties;
		this.clock = clock;
		this.dummyPasswordHash = passwordEncoder.encode("timing-equalization-only");
	}

	/**
	 * Registers a learner account. If the email is already registered nothing happens and
	 * no error is returned, so the endpoint cannot be used to find out who has an account.
	 */
	@Transactional
	public void register(String email, String password, String displayName, String ipAddress) {
		String normalizedEmail = normalizeEmail(email);
		// Hash first, so both branches take the same time
		String passwordHash = this.passwordEncoder.encode(password);
		if (this.users.existsByEmail(normalizedEmail)) {
			logger.info("Registration attempted for an existing account");
			return;
		}
		User user = this.users.save(
				new User(normalizedEmail, passwordHash, displayName.strip(), Role.LEARNER, this.clock.instant()));
		this.audit.record(AuditEventType.USER_REGISTERED, user.getId(), ipAddress, null);
	}

	/**
	 * Checks email and password. Failed attempts are committed (not rolled back) so the
	 * lockout counter works.
	 */
	@Transactional(noRollbackFor = AuthenticationFailedException.class)
	public LoginResult login(String email, String password, String ipAddress) {
		Instant now = this.clock.instant();
		Optional<User> found = this.users.findByEmail(normalizeEmail(email));
		if (found.isEmpty()) {
			this.passwordEncoder.matches(password, this.dummyPasswordHash);
			this.audit.record(AuditEventType.LOGIN_FAILED, null, ipAddress, "unknown account");
			throw AuthenticationFailedException.invalidCredentials();
		}
		User user = found.get();
		boolean passwordMatches = this.passwordEncoder.matches(password, user.getPasswordHash());
		if (!user.canLogIn(now)) {
			this.audit.record(AuditEventType.LOGIN_FAILED, user.getId(), ipAddress, "account locked or disabled");
			throw AuthenticationFailedException.invalidCredentials();
		}
		if (!passwordMatches) {
			registerFailure(user, AuditEventType.LOGIN_FAILED, ipAddress, now);
			throw AuthenticationFailedException.invalidCredentials();
		}
		if (this.passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
			user.changePasswordHash(this.passwordEncoder.encode(password), now);
		}
		if (this.mfa.isEnabled(user.getId())) {
			// Failed-attempt counter is reset only after the second factor, otherwise an
			// attacker who knows the password could guess MFA codes without ever locking.
			return LoginResult.mfaRequired(user.getId());
		}
		user.resetFailedAttempts(now);
		this.audit.record(AuditEventType.LOGIN_SUCCEEDED, user.getId(), ipAddress, null);
		return LoginResult.authenticated(authentication(user, false), user.requiresMfa());
	}

	/** Second login step: checks an authenticator or recovery code. */
	@Transactional(noRollbackFor = AuthenticationFailedException.class)
	public Authentication completeMfaLogin(long userId, String code, String ipAddress) {
		Instant now = this.clock.instant();
		User user = this.users.findById(userId).orElseThrow(AuthenticationFailedException::noPendingLogin);
		if (!user.canLogIn(now)) {
			this.audit.record(AuditEventType.MFA_CHALLENGE_FAILED, user.getId(), ipAddress, "account locked or disabled");
			throw AuthenticationFailedException.invalidCode();
		}
		if (!this.mfa.verifyChallenge(userId, code, ipAddress)) {
			registerFailure(user, AuditEventType.MFA_CHALLENGE_FAILED, ipAddress, now);
			throw AuthenticationFailedException.invalidCode();
		}
		user.resetFailedAttempts(now);
		this.audit.record(AuditEventType.MFA_CHALLENGE_PASSED, user.getId(), ipAddress, null);
		this.audit.record(AuditEventType.LOGIN_SUCCEEDED, user.getId(), ipAddress, null);
		return authentication(user, true);
	}

	/** Authentication for a user who just proved a second factor while logged in. */
	@Transactional(readOnly = true)
	public Authentication mfaVerifiedAuthentication(long userId) {
		User user = this.users.findById(userId).orElseThrow();
		return authentication(user, true);
	}

	private void registerFailure(User user, AuditEventType type, String ipAddress, Instant now) {
		this.audit.record(type, user.getId(), ipAddress, null);
		AppProperties.Lockout lockout = this.properties.lockout();
		if (user.registerFailedAttempt(lockout.maxFailedAttempts(), lockout.duration(), now)) {
			this.audit.record(AuditEventType.ACCOUNT_LOCKED, user.getId(), ipAddress,
					"locked for " + lockout.duration());
		}
	}

	private static Authentication authentication(User user, boolean mfaVerified) {
		return UsernamePasswordAuthenticationToken.authenticated(new AuthenticatedUser(user.getId(), user.getEmail()),
				null, Authorities.of(user, mfaVerified));
	}

	static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	/**
	 * Outcome of the password step.
	 *
	 * @param authentication set when login is complete
	 * @param pendingMfaUserId set when a second factor is still required
	 * @param mfaSetupRequired the user's role requires MFA but none is configured yet
	 */
	public record LoginResult(Authentication authentication, Long pendingMfaUserId, boolean mfaSetupRequired) {

		static LoginResult authenticated(Authentication authentication, boolean mfaSetupRequired) {
			return new LoginResult(authentication, null, mfaSetupRequired);
		}

		static LoginResult mfaRequired(long userId) {
			return new LoginResult(null, userId, false);
		}

		public boolean isMfaRequired() {
			return this.pendingMfaUserId != null;
		}

	}

}
