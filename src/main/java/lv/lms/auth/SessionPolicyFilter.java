package lv.lms.auth;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import lv.lms.audit.AuditEventType;
import lv.lms.audit.AuditService;
import lv.lms.config.AppProperties;
import lv.lms.user.UserRepository;
import lv.lms.user.UserStatus;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ends a logged-in session when it is no longer allowed. Runs on every request right after
 * the session's login is loaded:
 * <ul>
 * <li>the account was disabled (or deleted) since login (W-05);</li>
 * <li>the session is older than the absolute timeout, 8 hours for staff (AUTH-15).</li>
 * </ul>
 * The request then continues as a logged-out request, so protected endpoints answer 401
 * and public ones still work. The idle timeout is enforced by the servlet container (see
 * {@link LoginSession}).
 */
public class SessionPolicyFilter extends OncePerRequestFilter {

	private final UserRepository users;

	private final AuditService audit;

	private final AppProperties.Session sessionProperties;

	private final Clock clock;

	private final SecurityContextHolderStrategy holderStrategy = SecurityContextHolder.getContextHolderStrategy();

	public SessionPolicyFilter(UserRepository users, AuditService audit, AppProperties properties, Clock clock) {
		this.users = users;
		this.audit = audit;
		this.sessionProperties = properties.session();
		this.clock = clock;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = this.holderStrategy.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
			String reason = endReason(user);
			if (reason != null) {
				end(request);
				this.audit.record(AuditEventType.SESSION_ENDED, user.id(), request.getRemoteAddr(), reason);
			}
		}
		chain.doFilter(request, response);
	}

	/** @return why the session must end, or {@code null} if it may continue */
	private String endReason(AuthenticatedUser user) {
		Instant expiresAt = user.authenticatedAt().plus(this.sessionProperties.forUser(user.staff()).absolute());
		if (!this.clock.instant().isBefore(expiresAt)) {
			return "absolute timeout";
		}
		// One indexed lookup per request; keeps disabling an account effective immediately
		if (this.users.findStatusById(user.id()).filter(UserStatus.ACTIVE::equals).isEmpty()) {
			return "account disabled";
		}
		return null;
	}

	private void end(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		this.holderStrategy.clearContext();
	}

}
