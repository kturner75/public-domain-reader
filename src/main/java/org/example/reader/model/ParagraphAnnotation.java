package org.example.reader.model;

import java.time.LocalDateTime;

public record ParagraphAnnotation(
        String chapterId,
        int paragraphIndex,
        boolean highlighted,
        String noteText,
        boolean bookmarked,
        LocalDateTime updatedAt
) {
}
