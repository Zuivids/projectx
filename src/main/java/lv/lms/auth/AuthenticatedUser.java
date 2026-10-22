package lv.lms.auth;

import java.io.Serial;
import java.io.Serializable;

/** Principal stored in the session. Holds no secrets, only what is needed to identify the user. */
public record AuthenticatedUser(long id, String email) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

}
