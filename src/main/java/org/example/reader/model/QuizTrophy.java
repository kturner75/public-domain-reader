package org.example.reader.model;

import java.time.LocalDateTime;

public record QuizTrophy(
        String code,
        String title,
        String description,
        LocalDateTime unlockedAt
) {
}
