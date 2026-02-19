ALTER TABLE quiz_attempts
    ADD COLUMN reader_id VARCHAR(120);

CREATE INDEX idx_quiz_attempts_reader_created
    ON quiz_attempts (reader_id, created_at);

ALTER TABLE quiz_trophies
    ADD COLUMN reader_id VARCHAR(120);

CREATE UNIQUE INDEX uk_quiz_trophies_reader_book_code
    ON quiz_trophies (reader_id, book_id, code);

CREATE INDEX idx_quiz_trophies_reader_book_unlocked
    ON quiz_trophies (reader_id, book_id, unlocked_at);

CREATE TABLE user_reader_states (
    user_id VARCHAR(255) PRIMARY KEY,
    state_json TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_user_reader_states_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE user_reader_claims (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    reader_id VARCHAR(120) NOT NULL,
    claimed_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_user_reader_claims_user_reader UNIQUE (user_id, reader_id),
    CONSTRAINT fk_user_reader_claims_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_reader_claims_user
    ON user_reader_claims (user_id);
