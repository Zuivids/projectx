package lv.lms.user;

public enum Role {

	LEARNER, ADMIN, SUPER_ADMIN;

	/** Spring Security authority name for this role, e.g. {@code ROLE_ADMIN}. */
	public String authority() {
		return "ROLE_" + name();
	}

	/** Staff roles must always use MFA (requirement AUTH-06). */
	public boolean requiresMfa() {
		return this != LEARNER;
	}

}
