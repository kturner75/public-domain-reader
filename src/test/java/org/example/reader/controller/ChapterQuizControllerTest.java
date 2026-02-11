package org.example.reader.controller;

import org.example.reader.model.ChapterQuizGradeResponse;
import org.example.reader.model.ChapterQuizResponse;
import org.example.reader.model.ChapterQuizStatusResponse;
import org.example.reader.model.ChapterQuizViewPayload;
import org.example.reader.model.QuizProgress;
import org.example.reader.model.QuizTrophy;
import org.example.reader.service.QuizProgressService;
import org.example.reader.service.ChapterQuizService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChapterQuizController.class)
@TestPropertySource(properties = {
        "generation.cache-only=false"
})
class ChapterQuizControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChapterQuizService chapterQuizService;

    @MockitoBean
    private QuizProgressService quizProgressService;

    @Test
    void getStatus_returnsFeatureState() throws Exception {
        when(chapterQuizService.isProviderAvailable()).thenReturn(false);

        mockMvc.perform(get("/api/quizzes/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(true)))
                .andExpect(jsonPath("$.reasoningEnabled", is(true)))
                .andExpect(jsonPath("$.cacheOnly", is(false)))
                .andExpect(jsonPath("$.providerAvailable", is(false)))
                .andExpect(jsonPath("$.available", is(true)));
    }

    @Test
    void getChapterQuiz_existingChapter_returnsPayload() throws Exception {
        ChapterQuizResponse response = new ChapterQuizResponse(
                "book-1",
                "chapter-1",
                0,
                "Chapter 1",
                "COMPLETED",
                true,
                LocalDateTime.of(2026, 2, 11, 10, 30),
                LocalDateTime.of(2026, 2, 11, 10, 31),
                "v1",
                "grok",
                1,
                new ChapterQuizViewPayload(List.of(
                        new ChapterQuizViewPayload.Question(
                                "What does Holmes examine?",
                                List.of("A clue", "A coin", "A map", "A letter")
                        )
                ))
        );
        when(chapterQuizService.findBookIdForChapter("chapter-1")).thenReturn(Optional.of("book-1"));
        when(chapterQuizService.getChapterQuiz("chapter-1")).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/quizzes/chapter/chapter-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId", is("book-1")))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.payload.questions[0].question", is("What does Holmes examine?")));
    }

    @Test
    void getChapterQuizStatus_existingChapter_returnsStatus() throws Exception {
        ChapterQuizStatusResponse response = new ChapterQuizStatusResponse(
                "book-1",
                "chapter-1",
                "PENDING",
                false,
                null,
                LocalDateTime.of(2026, 2, 11, 9, 0)
        );
        when(chapterQuizService.findBookIdForChapter("chapter-1")).thenReturn(Optional.of("book-1"));
        when(chapterQuizService.getChapterQuizStatus("chapter-1")).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/quizzes/chapter/chapter-1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId", is("book-1")))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.ready", is(false)));
    }

    @Test
    void requestChapterQuizGeneration_existingChapter_returnsAccepted() throws Exception {
        when(chapterQuizService.findBookIdForChapter("chapter-1")).thenReturn(Optional.of("book-1"));

        mockMvc.perform(post("/api/quizzes/chapter/chapter-1/generate"))
                .andExpect(status().isAccepted());

        verify(chapterQuizService).requestChapterQuiz("chapter-1");
    }

    @Test
    void gradeQuiz_returnsScore() throws Exception {
        ChapterQuizGradeResponse graded = new ChapterQuizGradeResponse(
                "book-1",
                "chapter-1",
                3,
                2,
                67,
                1,
                List.of(),
                new QuizProgress(5, 2, 1),
                List.of(
                        new ChapterQuizGradeResponse.QuestionResult(
                                0,
                                "Question 1",
                                1,
                                1,
                                true,
                                "Answer 1",
                                0,
                                "Citation"
                        )
                )
        );
        when(chapterQuizService.findBookIdForChapter("chapter-1")).thenReturn(Optional.of("book-1"));
        when(chapterQuizService.gradeQuiz("chapter-1", List.of(1, 0, 2))).thenReturn(Optional.of(graded));

        mockMvc.perform(post("/api/quizzes/chapter/chapter-1/grade")
                        .contentType("application/json")
                        .content("""
                                {
                                  "selectedOptionIndexes": [1, 0, 2]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scorePercent", is(67)))
                .andExpect(jsonPath("$.correctAnswers", is(2)));
    }

    @Test
    void getBookTrophies_returnsUnlockedTrophies() throws Exception {
        when(quizProgressService.getBookTrophies("book-1")).thenReturn(List.of(
                new QuizTrophy(
                        "quiz_first_attempt",
                        "First Checkpoint",
                        "Complete your first chapter quiz.",
                        LocalDateTime.of(2026, 2, 11, 12, 0)
                )
        ));

        mockMvc.perform(get("/api/quizzes/book/book-1/trophies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code", is("quiz_first_attempt")))
                .andExpect(jsonPath("$[0].title", is("First Checkpoint")));
    }
}
