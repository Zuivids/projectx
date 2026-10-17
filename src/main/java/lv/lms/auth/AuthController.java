package lv.lms.auth;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import lv.lms.auth.dto.LoginRequest;
import lv.lms.auth.dto.LoginResponse;
import lv.lms.auth.dto.MfaCodeRequest;
import lv.lms.auth.dto.RegisterRequest;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	private final AuthService authService;

	private final LoginSession loginSession;

	AuthController(AuthService authService, LoginSession loginSession) {
		this.authService = authService;
		this.loginSession = loginSession;
	}

	/**
	 * Sets the {@code XSRF-TOKEN} cookie. Single-page apps call this once before the first
	 * POST, then copy the cookie value into the {@code X-XSRF-TOKEN} header of every
	 * state-changing request.
	 */
	@GetMapping("/csrf")
	Map<String, String> csrf(CsrfToken token) {
		// Reading the token makes Spring Security write the cookie
		token.getToken();
		return Map.of("cookieName", "XSRF-TOKEN", "headerName", token.getHeaderName());
	}

	/** Always 202, whether or not the email already exists (no account enumeration). */
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
		this.authService.register(body.email(), body.password(), body.displayName(), request.getRemoteAddr());
	}

	@PostMapping("/login")
	LoginResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		AuthService.LoginResult result = this.authService.login(body.email(), body.password(),
				request.getRemoteAddr());
		if (result.isMfaRequired()) {
			this.loginSession.startPendingMfa(result.pendingMfaUserId(), request, response);
			return new LoginResponse(LoginResponse.Status.MFA_REQUIRED, false);
		}
		this.loginSession.establish(result.authentication(), request, response);
		return new LoginResponse(LoginResponse.Status.AUTHENTICATED, result.mfaSetupRequired());
	}

	@PostMapping("/mfa")
	LoginResponse completeMfa(@Valid @RequestBody MfaCodeRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		long userId = this.loginSession.pendingMfaUserId(request)
			.orElseThrow(AuthenticationFailedException::noPendingLogin);
		Authentication authentication = this.authService.completeMfaLogin(userId, body.code(),
				request.getRemoteAddr());
		this.loginSession.establish(authentication, request, response);
		return new LoginResponse(LoginResponse.Status.AUTHENTICATED, false);
	}

}
