package lv.lms.user.admin;

import jakarta.servlet.http.HttpServletRequest;

import lv.lms.audit.Actor;
import lv.lms.auth.AuthenticatedUser;
import lv.lms.common.PageResponse;
import lv.lms.user.Role;
import lv.lms.user.UserStatus;
import lv.lms.user.admin.UserAdminResponses.UserDetail;
import lv.lms.user.admin.UserAdminResponses.UserSummary;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** User management; under /api/v1/admin, so it needs an admin role and MFA (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/users")
class UserAdminController {

	private final UserAdminService service;

	UserAdminController(UserAdminService service) {
		this.service = service;
	}

	@GetMapping
	PageResponse<UserSummary> list(@RequestParam(required = false) String query,
			@RequestParam(required = false) UserStatus status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return this.service.list(query, status, page, size);
	}

	@GetMapping("/{userId}")
	UserDetail get(@PathVariable long userId) {
		return this.service.get(userId);
	}

	@PostMapping("/{userId}/disable")
	UserDetail disable(@PathVariable long userId, @AuthenticationPrincipal AuthenticatedUser user,
			HttpServletRequest request) {
		return this.service.disable(userId, actor(user, request));
	}

	@PostMapping("/{userId}/enable")
	UserDetail enable(@PathVariable long userId, @AuthenticationPrincipal AuthenticatedUser user,
			HttpServletRequest request) {
		return this.service.enable(userId, actor(user, request));
	}

	@PostMapping("/{userId}/unlock")
	UserDetail unlock(@PathVariable long userId, @AuthenticationPrincipal AuthenticatedUser user,
			HttpServletRequest request) {
		return this.service.unlock(userId, actor(user, request));
	}

	@PostMapping("/{userId}/reset-mfa")
	UserDetail resetMfa(@PathVariable long userId, @AuthenticationPrincipal AuthenticatedUser user,
			HttpServletRequest request) {
		return this.service.resetMfa(userId, actor(user, request));
	}

	@PutMapping("/{userId}/roles/{role}")
	UserDetail grantRole(@PathVariable long userId, @PathVariable Role role,
			@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
		return this.service.grantRole(userId, role, actor(user, request));
	}

	@DeleteMapping("/{userId}/roles/{role}")
	UserDetail revokeRole(@PathVariable long userId, @PathVariable Role role,
			@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
		return this.service.revokeRole(userId, role, actor(user, request));
	}

	private static Actor actor(AuthenticatedUser user, HttpServletRequest request) {
		return new Actor(user.id(), request.getRemoteAddr());
	}

}
