-- Users, roles, TOTP MFA, recovery codes and the security audit log.
-- All timestamps are stored in UTC.

CREATE TABLE app_user (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    email              VARCHAR(320) NOT NULL,
    password_hash      VARCHAR(255) NOT NULL,
    display_name       VARCHAR(100) NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    email_verified_at  DATETIME(6)  NULL,
    failed_login_count INT          NOT NULL DEFAULT 0,
    locked_until       DATETIME(6)  NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_app_user_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_role (
    user_id BIGINT      NOT NULL,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- One TOTP authenticator per user. The secret is AES-GCM encrypted by the application.
-- confirmed_at is NULL until the user proves the authenticator works.
CREATE TABLE mfa_totp (
    user_id           BIGINT        NOT NULL,
    secret_ciphertext VARBINARY(255) NOT NULL,
    confirmed_at      DATETIME(6)   NULL,
    last_used_step    BIGINT        NULL,
    created_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_mfa_totp_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Single-use recovery codes, stored as SHA-256 hex hashes.
CREATE TABLE mfa_recovery_code (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    code_hash  VARCHAR(64) NOT NULL,
    used_at    DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_mfa_recovery_code UNIQUE (user_id, code_hash),
    CONSTRAINT fk_mfa_recovery_code_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Append-only security audit log. No foreign keys, so entries survive user deletion.
CREATE TABLE audit_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    occurred_at DATETIME(6)  NOT NULL,
    event_type  VARCHAR(50)  NOT NULL,
    user_id     BIGINT       NULL,
    ip_address  VARCHAR(45)  NULL,
    details     VARCHAR(500) NULL,
    PRIMARY KEY (id),
    INDEX ix_audit_log_user (user_id, occurred_at),
    INDEX ix_audit_log_occurred (occurred_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
