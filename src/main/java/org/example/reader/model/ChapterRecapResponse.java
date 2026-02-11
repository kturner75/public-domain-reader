package org.example.reader.model;

import java.time.LocalDateTime;

public record ChapterRecapResponse(
        String bookId,
        String chapterId,
        int chapterIndex,
        String chapterTitle,
        String status,
        boolean ready,
        LocalDateTime generatedAt,
        LocalDateTime updatedAt,
        String promptVersion,
        String modelName,
        ChapterRecapPayload payload
) {
}
