package lv.lms.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Error returned to API clients as an RFC 9457 problem detail. The detail text is sent
 * to the client, so it must never contain internal information.
 */
public class ApiException extends ErrorResponseException {

	public ApiException(HttpStatus status, String detail) {
		super(status, ProblemDetail.forStatusAndDetail(status, detail), null);
	}

	public static ApiException conflict(String detail) {
		return new ApiException(HttpStatus.CONFLICT, detail);
	}

	public static ApiException notFound(String detail) {
		return new ApiException(HttpStatus.NOT_FOUND, detail);
	}

	public static ApiException badRequest(String detail) {
		return new ApiException(HttpStatus.BAD_REQUEST, detail);
	}

}
