ALTER TABLE paragraph_annotations
    ADD COLUMN user_id VARCHAR(255);

CREATE INDEX idx_paragraph_annotations_user_book
    ON paragraph_annotations (user_id, book_id);

CREATE UNIQUE INDEX uk_paragraph_annotations_user_target
    ON paragraph_annotations (user_id, book_id, chapter_id, paragraph_index);

ALTER TABLE quiz_attempts
    ADD COLUMN user_id VARCHAR(255);

CREATE INDEX idx_quiz_attempts_user_book_created
    ON quiz_attempts (user_id, created_at);

ALTER TABLE quiz_trophies
    ADD COLUMN user_id VARCHAR(255);

ALTER TABLE quiz_trophies
    DROP CONSTRAINT uk_quiz_trophies_book_code;

ALTER TABLE quiz_trophies
    ADD CONSTRAINT uk_quiz_trophies_user_book_code UNIQUE (user_id, book_id, code);

CREATE INDEX idx_quiz_trophies_user_book_unlocked
    ON quiz_trophies (user_id, book_id, unlocked_at);
