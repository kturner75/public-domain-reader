ALTER TABLE users
    ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD COLUMN login_locked_until TIMESTAMP;

CREATE INDEX idx_users_login_locked_until
    ON users (login_locked_until);
