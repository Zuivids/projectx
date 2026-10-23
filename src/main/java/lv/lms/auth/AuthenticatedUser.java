package lv.lms.auth;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Principal stored in the session. Holds no secrets, only what is needed to identify the
 * user and apply the session rules.
 *
 * @param id user id
 * @param email user's email
 * @param staff the user has a staff role (admin), which means shorter session timeouts
 * @param authenticatedAt when the login completed; used for the absolute session timeout
 * @param securityVersion the user's security version at login; the session ends when it
 * changes (blocked, roles changed, MFA reset)
 */
public record AuthenticatedUser(long id, String email, boolean staff, Instant authenticatedAt, long securityVersion)
		implements Serializable {

	@Serial
	private static final long serialVersionUID = 3L;

}
