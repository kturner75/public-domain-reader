package org.example.reader.model;

public record QuizProgress(
        long totalAttempts,
        long perfectAttempts,
        int currentPerfectStreak
) {
}
