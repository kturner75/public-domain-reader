package org.example.reader.service;

import org.example.reader.entity.BookEntity;
import org.example.reader.entity.ChapterEntity;
import org.example.reader.entity.QuizAttemptEntity;
import org.example.reader.entity.QuizTrophyEntity;
import org.example.reader.model.QuizProgress;
import org.example.reader.model.QuizTrophy;
import org.example.reader.repository.QuizAttemptRepository;
import org.example.reader.repository.QuizTrophyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class QuizProgressService {

    private static final TrophyDefinition FIRST_ATTEMPT = new TrophyDefinition(
            "quiz_first_attempt",
            "First Checkpoint",
            "Complete your first chapter quiz."
    );

    private static final TrophyDefinition FIRST_PERFECT = new TrophyDefinition(
            "quiz_first_perfect",
            "Perfect Recall",
            "Score 100% on any chapter quiz."
    );

    private static final TrophyDefinition PERFECT_STREAK_THREE = new TrophyDefinition(
            "quiz_perfect_streak_3",
            "Hot Streak",
            "Get 3 perfect quiz scores in a row."
    );

    private static final TrophyDefinition HARD_MODE_PERFECT = new TrophyDefinition(
            "quiz_hard_mode_perfect",
            "High Difficulty Ace",
            "Score 100% on a high-difficulty quiz."
    );

    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizTrophyRepository quizTrophyRepository;

    public QuizProgressService(
            QuizAttemptRepository quizAttemptRepository,
            QuizTrophyRepository quizTrophyRepository) {
        this.quizAttemptRepository = quizAttemptRepository;
        this.quizTrophyRepository = quizTrophyRepository;
    }

    @Transactional
    public ProgressUpdate recordAttemptAndEvaluate(
            ChapterEntity chapter,
            int scorePercent,
            int correctAnswers,
            int totalQuestions,
            int difficultyLevel) {
        QuizAttemptEntity attempt = new QuizAttemptEntity();
        attempt.setChapter(chapter);
        attempt.setScorePercent(scorePercent);
        attempt.setCorrectAnswers(correctAnswers);
        attempt.setTotalQuestions(totalQuestions);
        attempt.setPerfect(scorePercent == 100);
        attempt.setDifficultyLevel(difficultyLevel);
        quizAttemptRepository.save(attempt);

        String bookId = chapter.getBook().getId();
        long totalAttempts = quizAttemptRepository.countByChapterBookId(bookId);
        long perfectAttempts = quizAttemptRepository.countByChapterBookIdAndPerfectTrue(bookId);
        int currentPerfectStreak = calculateCurrentPerfectStreak(bookId);

        List<QuizTrophy> unlocked = new ArrayList<>();
        unlockIfEligible(chapter.getBook(), FIRST_ATTEMPT, totalAttempts >= 1, unlocked);
        unlockIfEligible(chapter.getBook(), FIRST_PERFECT, scorePercent == 100, unlocked);
        unlockIfEligible(chapter.getBook(), PERFECT_STREAK_THREE, currentPerfectStreak >= 3, unlocked);
        unlockIfEligible(chapter.getBook(), HARD_MODE_PERFECT, scorePercent == 100 && difficultyLevel >= 2, unlocked);

        return new ProgressUpdate(
                unlocked,
                new QuizProgress(totalAttempts, perfectAttempts, currentPerfectStreak)
        );
    }

    @Transactional(readOnly = true)
    public List<QuizTrophy> getBookTrophies(String bookId) {
        return quizTrophyRepository.findByBookIdOrderByUnlockedAtDesc(bookId).stream()
                .map(this::toModel)
                .toList();
    }

    private int calculateCurrentPerfectStreak(String bookId) {
        List<QuizAttemptEntity> recentAttempts = quizAttemptRepository.findByChapterBookIdOrderByCreatedAtDesc(bookId);
        int streak = 0;
        for (QuizAttemptEntity attempt : recentAttempts) {
            if (attempt.isPerfect()) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    private void unlockIfEligible(
            BookEntity book,
            TrophyDefinition trophy,
            boolean eligible,
            List<QuizTrophy> unlocked) {
        if (!eligible) {
            return;
        }
        String bookId = book.getId();
        if (quizTrophyRepository.findByBookIdAndCode(bookId, trophy.code()).isPresent()) {
            return;
        }

        QuizTrophyEntity entity = new QuizTrophyEntity();
        entity.setBook(book);
        entity.setCode(trophy.code());
        entity.setTitle(trophy.title());
        entity.setDescription(trophy.description());

        try {
            QuizTrophyEntity saved = quizTrophyRepository.save(entity);
            unlocked.add(toModel(saved));
        } catch (DataIntegrityViolationException ignored) {
            // Duplicate unlock race; safe to ignore.
        }
    }

    private QuizTrophy toModel(QuizTrophyEntity entity) {
        return new QuizTrophy(
                entity.getCode(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getUnlockedAt()
        );
    }

    public record ProgressUpdate(
            List<QuizTrophy> newlyUnlocked,
            QuizProgress progress
    ) {
    }

    private record TrophyDefinition(
            String code,
            String title,
            String description
    ) {
    }
}
