package lv.lms.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lv.lms.config.AppProperties;

import org.springframework.stereotype.Component;

/**
 * Counts failed password and MFA attempts per account and IP address. After too many
 * failures that IP is blocked for that account for a while. An attacker who keeps
 * guessing blocks only their own address, while the real user can still log in from
 * theirs.
 *
 * <p>
 * Kept in memory (per instance, reset on restart). The account-wide lock in the
 * database is the durable second line of defence.
 */
@Component
class LoginAttemptTracker {

	private static final int MAX_KEYS = 100_000;

	private final Cache<String, Attempts> attempts;

	private final AppProperties.Lockout lockout;

	private final Clock clock;

	LoginAttemptTracker(AppProperties properties, Clock clock) {
		this.lockout = properties.lockout();
		this.clock = clock;
		this.attempts = Caffeine.newBuilder()
			.maximumSize(MAX_KEYS)
			// Failures older than this no longer count
			.expireAfterWrite(this.lockout.duration())
			.build();
	}

	boolean isBlocked(long userId, String ip) {
		Attempts current = this.attempts.getIfPresent(key(userId, ip));
		return current != null && current.blockedUntil() != null && current.blockedUntil().isAfter(this.clock.instant());
	}

	/** @return {@code true} if this failure blocked the IP for the account */
	boolean recordFailure(long userId, String ip) {
		Instant now = this.clock.instant();
		Duration duration = this.lockout.duration();
		Attempts updated = this.attempts.asMap().compute(key(userId, ip), (k, previous) -> {
			int failures = (previous == null || previous.blockedUntil() != null) ? 1 : previous.failures() + 1;
			Instant blockedUntil = failures >= this.lockout.maxFailedAttemptsPerIp() ? now.plus(duration) : null;
			return new Attempts(failures, blockedUntil);
		});
		return updated.blockedUntil() != null;
	}

	void reset(long userId, String ip) {
		this.attempts.invalidate(key(userId, ip));
	}

	private static String key(long userId, String ip) {
		return userId + "|" + ip;
	}

	private record Attempts(int failures, Instant blockedUntil) {
	}

}
