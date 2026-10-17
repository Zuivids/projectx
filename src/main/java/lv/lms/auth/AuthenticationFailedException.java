package lv.lms.auth;

import lv.lms.common.ApiException;

import org.springframework.http.HttpStatus;

/**
 * Failed login or MFA challenge. Wrong email, wrong password and a locked account all
 * produce the same message, so attackers cannot tell which accounts exist (AUTH-11).
 */
public class AuthenticationFailedException extends ApiException {

	private AuthenticationFailedException(String detail) {
		super(HttpStatus.UNAUTHORIZED, detail);
	}

	public static AuthenticationFailedException invalidCredentials() {
		return new AuthenticationFailedException("Invalid email or password");
	}

	public static AuthenticationFailedException invalidCode() {
		return new AuthenticationFailedException("Invalid verification code");
	}

	public static AuthenticationFailedException noPendingLogin() {
		return new AuthenticationFailedException("Login expired, please log in again");
	}

}
