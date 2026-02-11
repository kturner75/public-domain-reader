package org.example.reader.model;

import java.time.LocalDateTime;

public record ChapterQuizStatusResponse(
        String bookId,
        String chapterId,
        String status,
        boolean ready,
        LocalDateTime generatedAt,
        LocalDateTime updatedAt
) {
}
