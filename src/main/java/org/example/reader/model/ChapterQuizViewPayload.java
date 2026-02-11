package org.example.reader.model;

import java.util.List;

public record ChapterQuizViewPayload(
        List<Question> questions
) {
    public record Question(
            String question,
            List<String> options
    ) {
    }
}
