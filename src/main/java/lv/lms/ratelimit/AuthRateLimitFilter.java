package lv.lms.ratelimit;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lv.lms.config.AppProperties;
import lv.lms.config.AppProperties.RateLimit.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Limits login, MFA and registration requests per client IP (requirement AUTH-10). Runs
 * before Spring Security, so rejected requests cost almost nothing.
 *
 * <p>
 * Uses the connection's remote address. Behind a reverse proxy this is the proxy's
 * address until forwarded headers are configured (backlog W-06); until then every user
 * would share one limit.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

	private static final Logger logger = LoggerFactory.getLogger(AuthRateLimitFilter.class);

	private static final String PROBLEM_BODY = """
			{"type":"about:blank","title":"Too Many Requests","status":429,\
			"detail":"Too many attempts, please try again later"}""";

	private final RateLimiter rateLimiter;

	private final Map<String, Rule> rulesByPath;

	public AuthRateLimitFilter(RateLimiter rateLimiter, AppProperties properties) {
		this.rateLimiter = rateLimiter;
		AppProperties.RateLimit limits = properties.rateLimit();
		this.rulesByPath = Map.of("/api/v1/auth/login", limits.login(), "/api/v1/auth/mfa", limits.mfa(),
				"/api/v1/auth/register", limits.register());
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !"POST".equals(request.getMethod()) || !this.rulesByPath.containsKey(path(request));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String path = path(request);
		String ip = request.getRemoteAddr();
		RateLimiter.Decision decision = this.rateLimiter.tryAcquire(path + "|" + ip, this.rulesByPath.get(path));
		if (decision.allowed()) {
			chain.doFilter(request, response);
			return;
		}
		logger.warn("Rate limit exceeded for {} from {}", path, ip);
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		// Round up, so the client never retries a moment too early
		long seconds = Math.max(1, (decision.retryAfter().toMillis() + 999) / 1000);
		response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.getWriter().write(PROBLEM_BODY);
	}

	/**
	 * Decoded path without ";" parameters, as Spring MVC sees it. The raw request URI would
	 * let "/api/v1/auth/%6Cogin" slip past the limit and still reach the login endpoint.
	 */
	private static String path(HttpServletRequest request) {
		return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
	}

}
