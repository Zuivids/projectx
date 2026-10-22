package lv.lms.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Password rules follow NIST 800-63B: length matters, no composition rules (AUTH-03). */
public record RegisterRequest(
		@NotBlank @Email @Size(max = 320) String email,
		@NotBlank @Size(min = 12, max = 128) String password,
		@NotBlank @Size(max = 100) String displayName) {

	@Override
	public String toString() {
		return "RegisterRequest[email=" + this.email + ", password=***, displayName=" + this.displayName + "]";
	}

}
