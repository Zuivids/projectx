-- Incremented whenever a user's access changes (blocked, roles changed, MFA reset).
-- A session remembers the value from login and ends as soon as it no longer matches.
ALTER TABLE app_user
    ADD COLUMN security_version BIGINT NOT NULL DEFAULT 0 AFTER locked_until;

-- The user an admin action was about (user_id is the admin who did it).
ALTER TABLE audit_log
    ADD COLUMN target_user_id BIGINT NULL AFTER user_id,
    ADD INDEX ix_audit_log_target (target_user_id, occurred_at);
