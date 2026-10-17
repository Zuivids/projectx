package lv.lms.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Size(max = 320) String email, @NotBlank @Size(max = 128) String password) {

	@Override
	public String toString() {
		return "LoginRequest[email=" + this.email + ", password=***]";
	}

}
