CREATE TABLE paragraph_annotations (
    id VARCHAR(255) PRIMARY KEY,
    reader_id VARCHAR(120) NOT NULL,
    book_id VARCHAR(255) NOT NULL,
    chapter_id VARCHAR(255) NOT NULL,
    paragraph_index INTEGER NOT NULL,
    highlighted BOOLEAN NOT NULL DEFAULT FALSE,
    note_text TEXT,
    bookmarked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_paragraph_annotations_book FOREIGN KEY (book_id) REFERENCES books (id) ON DELETE CASCADE,
    CONSTRAINT fk_paragraph_annotations_chapter FOREIGN KEY (chapter_id) REFERENCES chapters (id) ON DELETE CASCADE,
    CONSTRAINT uk_paragraph_annotations_reader_target UNIQUE (reader_id, book_id, chapter_id, paragraph_index)
);

CREATE INDEX idx_paragraph_annotations_reader_book
    ON paragraph_annotations (reader_id, book_id);

CREATE INDEX idx_paragraph_annotations_reader_bookmarked
    ON paragraph_annotations (reader_id, book_id, bookmarked, updated_at);
