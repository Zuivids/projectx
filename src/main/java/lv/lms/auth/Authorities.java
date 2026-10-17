package lv.lms.auth;

import java.util.ArrayList;
import java.util.List;

import lv.lms.user.Role;
import lv.lms.user.User;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class Authorities {

	/** Granted only to sessions that passed a second factor in this login. */
	public static final String MFA_VERIFIED = "MFA_VERIFIED";

	private Authorities() {
	}

	public static List<GrantedAuthority> of(User user, boolean mfaVerified) {
		List<GrantedAuthority> authorities = new ArrayList<>();
		user.getRoles().stream().map(Role::authority).sorted().map(SimpleGrantedAuthority::new).forEach(authorities::add);
		if (mfaVerified) {
			authorities.add(new SimpleGrantedAuthority(MFA_VERIFIED));
		}
		return authorities;
	}

}
