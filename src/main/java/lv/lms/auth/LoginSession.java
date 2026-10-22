package lv.lms.auth;

import java.io.Serial;
import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import lv.lms.config.AppProperties;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Stores login state in the HTTP session. Between the password and MFA steps the session
 * holds only a short-lived "pending" marker and is not authenticated. The session id is
 * changed at every step to prevent session fixation.
 */
@Component
class LoginSession {

	private static final String PENDING_MFA = LoginSession.class.getName() + ".PENDING_MFA";

	private final SecurityContextRepository securityContextRepository;

	private final SecurityContextHolderStrategy holderStrategy = SecurityContextHolder.getContextHolderStrategy();

	private final AppProperties properties;

	private final Clock clock;

	LoginSession(SecurityContextRepository securityContextRepository, AppProperties properties, Clock clock) {
		this.securityContextRepository = securityContextRepository;
		this.properties = properties;
		this.clock = clock;
	}

	void startPendingMfa(long userId, HttpServletRequest request, HttpServletResponse response) {
		clearAuthentication(request, response);
		HttpSession session = rotateSession(request);
		session.setAttribute(PENDING_MFA,
				new PendingMfa(userId, this.clock.instant().plus(this.properties.mfa().pendingTimeout())));
	}

	Optional<Long> pendingMfaUserId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute(PENDING_MFA) instanceof PendingMfa pending)) {
			return Optional.empty();
		}
		if (pending.expiresAt().isBefore(this.clock.instant())) {
			session.removeAttribute(PENDING_MFA);
			return Optional.empty();
		}
		return Optional.of(pending.userId());
	}

	void establish(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = rotateSession(request);
		session.removeAttribute(PENDING_MFA);
		SecurityContext context = this.holderStrategy.createEmptyContext();
		context.setAuthentication(authentication);
		this.holderStrategy.setContext(context);
		this.securityContextRepository.saveContext(context, request, response);
	}

	private void clearAuthentication(HttpServletRequest request, HttpServletResponse response) {
		SecurityContext empty = this.holderStrategy.createEmptyContext();
		this.holderStrategy.setContext(empty);
		this.securityContextRepository.saveContext(empty, request, response);
	}

	private static HttpSession rotateSession(HttpServletRequest request) {
		if (request.getSession(false) != null) {
			request.changeSessionId();
			return request.getSession();
		}
		return request.getSession(true);
	}

	private record PendingMfa(long userId, Instant expiresAt) implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

	}

}
