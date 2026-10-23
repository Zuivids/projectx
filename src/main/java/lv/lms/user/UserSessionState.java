package lv.lms.user;

/** What a session needs to know about its user on every request. */
public record UserSessionState(UserStatus status, long securityVersion) {
}
