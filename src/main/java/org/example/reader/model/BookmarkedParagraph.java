package org.example.reader.model;

import java.time.LocalDateTime;

public record BookmarkedParagraph(
        String chapterId,
        String chapterTitle,
        int paragraphIndex,
        String snippet,
        LocalDateTime updatedAt
) {
}
