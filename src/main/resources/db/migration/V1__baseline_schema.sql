CREATE TABLE books (
    id VARCHAR(255) PRIMARY KEY,
    author VARCHAR(255) NOT NULL,
    character_enabled BOOLEAN,
    character_prefetch_completed BOOLEAN,
    cover_url VARCHAR(255),
    description VARCHAR(2000),
    illustration_enabled BOOLEAN,
    illustration_prompt_prefix VARCHAR(1000),
    illustration_setting VARCHAR(1000),
    illustration_style VARCHAR(255),
    illustration_style_reasoning VARCHAR(2000),
    source VARCHAR(255) NOT NULL,
    source_id VARCHAR(255),
    title VARCHAR(255) NOT NULL,
    tts_enabled BOOLEAN,
    tts_instructions VARCHAR(1000),
    tts_reasoning VARCHAR(500),
    tts_speed DOUBLE PRECISION,
    tts_voice VARCHAR(255)
);

CREATE TABLE chapters (
    id VARCHAR(255) PRIMARY KEY,
    book_id VARCHAR(255) NOT NULL,
    chapter_index INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    CONSTRAINT fk_chapters_book FOREIGN KEY (book_id) REFERENCES books (id)
);

CREATE TABLE paragraphs (
    id VARCHAR(255) PRIMARY KEY,
    chapter_id VARCHAR(255) NOT NULL,
    paragraph_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    CONSTRAINT fk_paragraphs_chapter FOREIGN KEY (chapter_id) REFERENCES chapters (id)
);

CREATE TABLE illustrations (
    id VARCHAR(255) PRIMARY KEY,
    chapter_id VARCHAR(255) NOT NULL,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    error_message VARCHAR(1000),
    generated_prompt VARCHAR(2000),
    image_filename VARCHAR(255),
    lease_expires_at TIMESTAMP,
    lease_owner VARCHAR(120),
    next_retry_at TIMESTAMP,
    retry_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(64) NOT NULL,
    CONSTRAINT uk_illustrations_chapter UNIQUE (chapter_id),
    CONSTRAINT fk_illustrations_chapter FOREIGN KEY (chapter_id) REFERENCES chapters (id)
);

CREATE TABLE chapter_analyses (
    id VARCHAR(255) PRIMARY KEY,
    chapter_id VARCHAR(255) NOT NULL,
    analyzed_at TIMESTAMP NOT NULL,
    character_count INTEGER NOT NULL,
    lease_expires_at TIMESTAMP,
    lease_owner VARCHAR(120),
    next_retry_at TIMESTAMP,
    retry_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(64),
    CONSTRAINT uk_chapter_analyses_chapter UNIQUE (chapter_id),
    CONSTRAINT fk_chapter_analyses_chapter FOREIGN KEY (chapter_id) REFERENCES chapters (id)
);

CREATE TABLE characters (
    id VARCHAR(255) PRIMARY KEY,
    book_id VARCHAR(255) NOT NULL,
    character_type VARCHAR(255) NOT NULL DEFAULT 'SECONDARY',
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    description VARCHAR(2000),
    error_message VARCHAR(1000),
    first_chapter_id VARCHAR(255) NOT NULL,
    first_paragraph_index INTEGER NOT NULL,
    lease_expires_at TIMESTAMP,
    lease_owner VARCHAR(120),
    name VARCHAR(255) NOT NULL,
    next_retry_at TIMESTAMP,
    portrait_filename VARCHAR(255),
    portrait_prompt VARCHAR(2000),
    retry_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(64) NOT NULL,
    CONSTRAINT uk_characters_book_name UNIQUE (book_id, name),
    CONSTRAINT fk_characters_book FOREIGN KEY (book_id) REFERENCES books (id),
    CONSTRAINT fk_characters_first_chapter FOREIGN KEY (first_chapter_id) REFERENCES chapters (id)
);

CREATE TABLE chapter_recaps (
    id VARCHAR(255) PRIMARY KEY,
    chapter_id VARCHAR(255) NOT NULL,
    generated_at TIMESTAMP,
    lease_expires_at TIMESTAMP,
    lease_owner VARCHAR(120),
    model_name VARCHAR(200),
    next_retry_at TIMESTAMP,
    payload_json TEXT,
    prompt_version VARCHAR(100),
    retry_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_chapter_recaps_chapter UNIQUE (chapter_id),
    CONSTRAINT fk_chapter_recaps_chapter FOREIGN KEY (chapter_id) REFERENCES chapters (id)
);

CREATE TABLE chapter_quizzes (
    id VARCHAR(255) PRIMARY KEY,
    chapter_id VARCHAR(255) NOT NULL,
    generated_at TIMESTAMP,
    model_name VARCHAR(200),
    payload_json TEXT,
    prompt_version VARCHAR(100),
    status VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_chapter_quizzes_chapter UNIQUE (chapter_id),
    CONSTRAINT fk_chapter_quizzes_chapter FOREIGN KEY (chapter_id) REFERENCES chapters (id)
);

CREATE TABLE quiz_attempts (
    id VARCHAR(255) PRIMARY KEY,
    chapter_id VARCHAR(255) NOT NULL,
    correct_answers INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    difficulty_level INTEGER NOT NULL,
    perfect BOOLEAN NOT NULL,
    score_percent INTEGER NOT NULL,
    total_questions INTEGER NOT NULL,
    CONSTRAINT fk_quiz_attempts_chapter FOREIGN KEY (chapter_id) REFERENCES chapters (id)
);

CREATE TABLE quiz_trophies (
    id VARCHAR(255) PRIMARY KEY,
    book_id VARCHAR(255) NOT NULL,
    code VARCHAR(64) NOT NULL,
    description VARCHAR(400) NOT NULL,
    title VARCHAR(120) NOT NULL,
    unlocked_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_quiz_trophies_book_code UNIQUE (book_id, code),
    CONSTRAINT fk_quiz_trophies_book FOREIGN KEY (book_id) REFERENCES books (id)
);

CREATE INDEX idx_chapters_book_chapter_index ON chapters (book_id, chapter_index);
CREATE INDEX idx_paragraphs_chapter_paragraph_index ON paragraphs (chapter_id, paragraph_index);
CREATE INDEX idx_illustrations_status ON illustrations (status);
CREATE INDEX idx_characters_status ON characters (status);
CREATE INDEX idx_chapter_analyses_status ON chapter_analyses (status);
CREATE INDEX idx_chapter_recaps_status ON chapter_recaps (status);
CREATE INDEX idx_chapter_quizzes_status ON chapter_quizzes (status);
