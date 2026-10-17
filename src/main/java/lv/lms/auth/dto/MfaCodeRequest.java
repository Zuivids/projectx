package lv.lms.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A 6-digit authenticator code, or a recovery code such as {@code K7QX2-MP4RA}. */
public record MfaCodeRequest(@NotBlank @Size(max = 32) String code) {

	@Override
	public String toString() {
		return "MfaCodeRequest[code=***]";
	}

}
