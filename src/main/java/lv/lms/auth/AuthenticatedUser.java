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
 */
public record AuthenticatedUser(long id, String email, boolean staff, Instant authenticatedAt)
		implements Serializable {

	@Serial
	private static final long serialVersionUID = 2L;

}
