package lv.lms.user;

import lv.lms.auth.AuthenticatedUser;
import lv.lms.auth.Authorities;
import lv.lms.auth.mfa.MfaService;
import lv.lms.user.dto.MeResponse;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
class MeController {

	private final UserRepository users;

	private final MfaService mfaService;

	MeController(UserRepository users, MfaService mfaService) {
		this.users = users;
		this.mfaService = mfaService;
	}

	@GetMapping
	MeResponse me(Authentication authentication) {
		AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
		User user = this.users.findById(principal.id()).orElseThrow();
		boolean mfaEnabled = this.mfaService.isEnabled(user.getId());
		boolean mfaVerified = authentication.getAuthorities()
			.stream()
			.anyMatch((a) -> Authorities.MFA_VERIFIED.equals(a.getAuthority()));
		return new MeResponse(user.getEmail(), user.getDisplayName(),
				user.getRoles().stream().map(Role::name).sorted().toList(), mfaEnabled, mfaVerified,
				user.requiresMfa() && !mfaEnabled);
	}

}
