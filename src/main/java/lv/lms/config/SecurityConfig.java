package lv.lms.config;

import java.util.Map;

import lv.lms.audit.AuditEventType;
import lv.lms.audit.AuditService;
import lv.lms.auth.AuthenticatedUser;
import lv.lms.auth.Authorities;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository,
			AuditService audit) throws Exception {
		http
			.securityContext((context) -> context.securityContextRepository(securityContextRepository))
			// Token in a readable XSRF-TOKEN cookie, sent back by the SPA in X-XSRF-TOKEN
			.csrf((csrf) -> csrf.spa())
			.authorizeHttpRequests((auth) -> auth
				.requestMatchers("/actuator/health", "/error").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/mfa")
				.permitAll()
				// Admin features need an admin role AND a second factor in this session (AUTH-06)
				.requestMatchers("/api/v1/admin/**").access(adminWithMfa())
				.anyRequest().authenticated())
			.exceptionHandling((exceptions) -> exceptions
				.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout((logout) -> logout
				.logoutUrl("/api/v1/auth/logout")
				.addLogoutHandler((request, response, authentication) -> {
					if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
						audit.record(AuditEventType.LOGGED_OUT, user.id(), request.getRemoteAddr(), null);
					}
				})
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			.headers((headers) -> headers
				.contentSecurityPolicy((csp) -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none'"))
				.httpStrictTransportSecurity((hsts) -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)));
		return http.build();
	}

	private static AuthorizationManager<RequestAuthorizationContext> adminWithMfa() {
		return AuthorizationManagers.allOf(AuthorityAuthorizationManager.hasAnyRole("ADMIN", "SUPER_ADMIN"),
				AuthorityAuthorizationManager.hasAuthority(Authorities.MFA_VERIFIED));
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	/**
	 * Argon2id for new hashes (AUTH-02). Hashes carry an {id} prefix, so the algorithm can
	 * be changed later and old hashes are upgraded on the next login.
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		Map<String, PasswordEncoder> encoders = Map.of("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
				"bcrypt", new BCryptPasswordEncoder());
		return new DelegatingPasswordEncoder("argon2", encoders);
	}

}
