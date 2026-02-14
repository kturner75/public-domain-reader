CREATE TABLE rate_limit_windows (
    window_key VARCHAR(255) PRIMARY KEY,
    scope_key VARCHAR(255) NOT NULL,
    window_start_epoch BIGINT NOT NULL,
    request_count INTEGER NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_rate_limit_windows_expires_at ON rate_limit_windows (expires_at);
