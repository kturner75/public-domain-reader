CREATE TABLE users (
    id VARCHAR(255) PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE user_sessions (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    token_hash VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    last_accessed_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_user_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_sessions_user
    ON user_sessions (user_id);

CREATE INDEX idx_user_sessions_expires
    ON user_sessions (expires_at);
