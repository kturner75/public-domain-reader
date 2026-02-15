package org.example.reader.controller;

import org.example.reader.model.ChapterQuizGradeResponse;
import org.example.reader.model.ChapterQuizResponse;
import org.example.reader.model.ChapterQuizStatusResponse;
import org.example.reader.model.QuizTrophy;
import org.example.reader.service.ChapterQuizService;
import org.example.reader.service.QuizProgressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quizzes")
public class ChapterQuizController {

    private static final Logger log = LoggerFactory.getLogger(ChapterQuizController.class);

    @Value("${quiz.enabled:true}")
    private boolean quizEnabled;

    @Value("${ai.reasoning.enabled:true}")
    private boolean reasoningEnabled;

    @Value("${generation.cache-only:false}")
    private boolean cacheOnly;

    @Value("${quiz.difficulty.ramp.enabled:true}")
    private boolean difficultyRampEnabled;

    @Value("${quiz.difficulty.ramp.chapter-step:6}")
    private int difficultyRampChapterStep;

    @Value("${quiz.difficulty.ramp.max-level:3}")
    private int difficultyRampMaxLevel;

    private final ChapterQuizService chapterQuizService;
    private final QuizProgressService quizProgressService;

    public ChapterQuizController(
            ChapterQuizService chapterQuizService,
            QuizProgressService quizProgressService) {
        this.chapterQuizService = chapterQuizService;
        this.quizProgressService = quizProgressService;
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", quizEnabled);
        status.put("reasoningEnabled", reasoningEnabled);
        status.put("cacheOnly", cacheOnly);
        status.put("providerAvailable", chapterQuizService.isProviderAvailable());
        status.put("available", isQuizFeatureEnabled());
        status.put("generationAvailable", isQuizGenerationAvailable());
        status.put("difficultyRampEnabled", difficultyRampEnabled);
        status.put("difficultyRampChapterStep", difficultyRampChapterStep);
        status.put("difficultyRampMaxLevel", difficultyRampMaxLevel);
        return status;
    }

    @GetMapping("/book/{bookId}/status")
    public Map<String, Object> getBookStatus(@PathVariable String bookId) {
        Map<String, Object> status = new HashMap<>();
        status.put("bookId", bookId);
        status.put("enabled", quizEnabled);
        status.put("reasoningEnabled", reasoningEnabled);
        status.put("cacheOnly", cacheOnly);
        status.put("providerAvailable", chapterQuizService.isProviderAvailable());
        status.put("available", isQuizFeatureEnabled());
        status.put("generationAvailable", isQuizGenerationAvailable());
        status.put("difficultyRampEnabled", difficultyRampEnabled);
        status.put("difficultyRampChapterStep", difficultyRampChapterStep);
        status.put("difficultyRampMaxLevel", difficultyRampMaxLevel);
        status.put("trophiesUnlocked", quizProgressService.getBookTrophies(bookId).size());
        return status;
    }

    @GetMapping("/book/{bookId}/trophies")
    public ResponseEntity<List<QuizTrophy>> getBookTrophies(@PathVariable String bookId) {
        if (!isQuizFeatureEnabled()) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(quizProgressService.getBookTrophies(bookId));
    }

    @GetMapping("/chapter/{chapterId}")
    public ResponseEntity<ChapterQuizResponse> getChapterQuiz(@PathVariable String chapterId) {
        if (!quizEnabled) {
            return ResponseEntity.status(403).build();
        }
        var bookId = chapterQuizService.findBookIdForChapter(chapterId).orElse(null);
        if (bookId == null) {
            log.warn("Quiz fetch requested for unknown chapter {}", chapterId);
            return ResponseEntity.notFound().build();
        }
        if (!isQuizFeatureEnabled()) {
            return ResponseEntity.status(403).build();
        }
        try {
            return chapterQuizService.getChapterQuiz(chapterId)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error(
                    "Failed to fetch quiz for chapter {} (bookId={}, cacheOnly={}, providerAvailable={})",
                    chapterId,
                    bookId,
                    cacheOnly,
                    chapterQuizService.isProviderAvailable(),
                    e
            );
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/chapter/{chapterId}/status")
    public ResponseEntity<ChapterQuizStatusResponse> getChapterQuizStatus(@PathVariable String chapterId) {
        if (!quizEnabled) {
            return ResponseEntity.status(403).build();
        }
        var bookId = chapterQuizService.findBookIdForChapter(chapterId).orElse(null);
        if (bookId == null) {
            log.warn("Quiz status requested for unknown chapter {}", chapterId);
            return ResponseEntity.notFound().build();
        }
        if (!isQuizFeatureEnabled()) {
            return ResponseEntity.status(403).build();
        }
        try {
            return chapterQuizService.getChapterQuizStatus(chapterId)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error(
                    "Failed to fetch quiz status for chapter {} (bookId={}, cacheOnly={}, providerAvailable={})",
                    chapterId,
                    bookId,
                    cacheOnly,
                    chapterQuizService.isProviderAvailable(),
                    e
            );
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/chapter/{chapterId}/generate")
    public ResponseEntity<Void> requestChapterQuizGeneration(@PathVariable String chapterId) {
        if (!isQuizGenerationConfigured()) {
            return ResponseEntity.status(403).build();
        }
        if (cacheOnly) {
            return ResponseEntity.status(409).build();
        }
        var bookId = chapterQuizService.findBookIdForChapter(chapterId).orElse(null);
        if (bookId == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            chapterQuizService.requestChapterQuiz(chapterId);
            return ResponseEntity.accepted().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/chapter/{chapterId}/grade")
    public ResponseEntity<ChapterQuizGradeResponse> gradeQuiz(
            @PathVariable String chapterId,
            @RequestBody QuizSubmissionRequest request) {
        if (!quizEnabled) {
            return ResponseEntity.status(403).build();
        }
        var bookId = chapterQuizService.findBookIdForChapter(chapterId).orElse(null);
        if (bookId == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isQuizFeatureEnabled()) {
            return ResponseEntity.status(403).build();
        }
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            return chapterQuizService.gradeQuiz(chapterId, request.selectedOptionIndexes())
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).build();
        }
    }

    private boolean isQuizFeatureEnabled() {
        return quizEnabled;
    }

    private boolean isQuizGenerationAvailable() {
        return isQuizGenerationConfigured() && !cacheOnly;
    }

    private boolean isQuizGenerationConfigured() {
        return quizEnabled && reasoningEnabled;
    }

    public record QuizSubmissionRequest(List<Integer> selectedOptionIndexes) {
    }
}
