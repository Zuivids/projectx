package lv.lms.auth.dto;

/**
 * @param status {@code AUTHENTICATED} when logged in, {@code MFA_REQUIRED} when the client
 * must send a code to {@code POST /api/v1/auth/mfa}
 * @param mfaSetupRequired the account must set up MFA before using admin features
 */
public record LoginResponse(Status status, boolean mfaSetupRequired) {

	public enum Status {

		AUTHENTICATED, MFA_REQUIRED

	}

}
