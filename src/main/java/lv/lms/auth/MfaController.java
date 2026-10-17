package lv.lms.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import lv.lms.auth.dto.MfaCodeRequest;
import lv.lms.auth.dto.RecoveryCodesResponse;
import lv.lms.auth.dto.TotpSetupResponse;
import lv.lms.auth.mfa.MfaService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticator app setup for the logged-in user. */
@RestController
@RequestMapping("/api/v1/me/mfa/totp")
class MfaController {

	private final MfaService mfaService;

	private final AuthService authService;

	private final LoginSession loginSession;

	MfaController(MfaService mfaService, AuthService authService, LoginSession loginSession) {
		this.mfaService = mfaService;
		this.authService = authService;
		this.loginSession = loginSession;
	}

	/** Step 1: get a secret and a QR code URI for the authenticator app. */
	@PostMapping
	TotpSetupResponse start(@AuthenticationPrincipal AuthenticatedUser user) {
		MfaService.TotpSetup setup = this.mfaService.startTotpSetup(user.id(), user.email());
		return new TotpSetupResponse(setup.secret(), setup.otpauthUri());
	}

	/**
	 * Step 2: prove the app works with a first code. Returns the recovery codes and
	 * upgrades the current session to MFA-verified.
	 */
	@PostMapping("/confirm")
	RecoveryCodesResponse confirm(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody MfaCodeRequest body, HttpServletRequest request, HttpServletResponse response) {
		RecoveryCodesResponse codes = new RecoveryCodesResponse(
				this.mfaService.confirmTotpSetup(user.id(), body.code(), request.getRemoteAddr()));
		this.loginSession.establish(this.authService.mfaVerifiedAuthentication(user.id()), request, response);
		return codes;
	}

}
