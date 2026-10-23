package lv.lms.audit;

/**
 * Who performed an action, for the audit log.
 *
 * @param userId the logged-in user
 * @param ipAddress the client address of the request
 */
public record Actor(long userId, String ipAddress) {
}
