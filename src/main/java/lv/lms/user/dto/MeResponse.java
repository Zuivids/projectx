package lv.lms.user.dto;

import java.util.List;

/**
 * @param mfaVerified this session passed a second factor
 * @param mfaSetupRequired the user's role requires MFA but none is configured yet
 */
public record MeResponse(String email, String displayName, List<String> roles, boolean mfaEnabled,
		boolean mfaVerified, boolean mfaSetupRequired) {
}
