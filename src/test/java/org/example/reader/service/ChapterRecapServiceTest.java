package org.example.reader.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.reader.entity.BookEntity;
import org.example.reader.entity.ChapterEntity;
import org.example.reader.entity.ChapterRecapEntity;
import org.example.reader.entity.ChapterRecapStatus;
import org.example.reader.entity.ParagraphEntity;
import org.example.reader.model.ChapterRecapPayload;
import org.example.reader.model.ChapterRecapResponse;
import org.example.reader.repository.CharacterRepository;
import org.example.reader.repository.ChapterRecapRepository;
import org.example.reader.repository.ChapterRepository;
import org.example.reader.repository.ParagraphRepository;
import org.example.reader.service.llm.LlmProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChapterRecapServiceTest {

    @Mock
    private ChapterRecapRepository chapterRecapRepository;

    @Mock
    private ChapterRepository chapterRepository;

    @Mock
    private ParagraphRepository paragraphRepository;

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private LlmProvider reasoningProvider;

    @Mock
    private RecapMetricsService recapMetricsService;

    private ChapterRecapService chapterRecapService;

    @BeforeEach
    void setUp() {
        chapterRecapService = new ChapterRecapService(
                chapterRecapRepository,
                chapterRepository,
                paragraphRepository,
                characterRepository,
                reasoningProvider,
                recapMetricsService,
                new ObjectMapper()
        );
    }

    @Test
    void getChapterRecap_withoutStoredRecap_returnsMissingPayload() {
        ChapterEntity chapter = createChapter("book-1", "chapter-1", 1, "Chapter 1");
        when(chapterRecapRepository.findByChapterIdWithChapterAndBook("chapter-1"))
                .thenReturn(Optional.empty());
        when(chapterRepository.findByIdWithBook("chapter-1"))
                .thenReturn(Optional.of(chapter));

        Optional<ChapterRecapResponse> result = chapterRecapService.getChapterRecap("chapter-1");

        assertTrue(result.isPresent());
        ChapterRecapResponse recap = result.get();
        assertEquals("book-1", recap.bookId());
        assertEquals("chapter-1", recap.chapterId());
        assertEquals("MISSING", recap.status());
        assertFalse(recap.ready());
        assertEquals("", recap.payload().shortSummary());
        assertEquals(List.of(), recap.payload().keyEvents());
        assertEquals(List.of(), recap.payload().characterDeltas());
    }

    @Test
    void getChapterRecap_withStoredRecap_returnsStructuredPayload() throws Exception {
        ChapterEntity chapter = createChapter("book-1", "chapter-1", 1, "Chapter 1");
        ChapterRecapEntity entity = new ChapterRecapEntity(chapter);
        entity.setStatus(ChapterRecapStatus.COMPLETED);
        entity.setGeneratedAt(LocalDateTime.of(2026, 2, 8, 12, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 2, 8, 12, 1));
        entity.setPromptVersion("v1");
        entity.setModelName("grok");
        entity.setPayloadJson(new ObjectMapper().writeValueAsString(new ChapterRecapPayload(
                "Summary",
                List.of("Event one"),
                List.of(new ChapterRecapPayload.CharacterDelta("Ishmael", "Grows more reflective"))
        )));
        when(chapterRecapRepository.findByChapterIdWithChapterAndBook("chapter-1"))
                .thenReturn(Optional.of(entity));

        Optional<ChapterRecapResponse> result = chapterRecapService.getChapterRecap("chapter-1");

        assertTrue(result.isPresent());
        ChapterRecapResponse recap = result.get();
        assertEquals("COMPLETED", recap.status());
        assertTrue(recap.ready());
        assertEquals("Summary", recap.payload().shortSummary());
        assertEquals("Event one", recap.payload().keyEvents().get(0));
        assertEquals("Ishmael", recap.payload().characterDeltas().get(0).characterName());
    }

    @Test
    void saveGeneratedRecap_persistsCompletedRecord() {
        ChapterEntity chapter = createChapter("book-1", "chapter-1", 1, "Chapter 1");
        when(chapterRepository.findById("chapter-1")).thenReturn(Optional.of(chapter));
        when(chapterRecapRepository.findByChapterId("chapter-1")).thenReturn(Optional.empty());
        when(chapterRecapRepository.save(any(ChapterRecapEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        chapterRecapService.saveGeneratedRecap(
                "chapter-1",
                new ChapterRecapPayload(
                        "Summary",
                        List.of("Event one"),
                        List.of(new ChapterRecapPayload.CharacterDelta("Ishmael", "Grows more reflective"))
                ),
                "v1",
                "grok"
        );

        ArgumentCaptor<ChapterRecapEntity> captor = ArgumentCaptor.forClass(ChapterRecapEntity.class);
        verify(chapterRecapRepository).save(captor.capture());
        ChapterRecapEntity saved = captor.getValue();
        assertEquals(ChapterRecapStatus.COMPLETED, saved.getStatus());
        assertNotNull(saved.getGeneratedAt());
        assertEquals("v1", saved.getPromptVersion());
        assertEquals("grok", saved.getModelName());
        assertNotNull(saved.getPayloadJson());
        assertTrue(saved.getPayloadJson().contains("Summary"));
    }

    @Test
    void saveGeneratedRecap_missingChapter_throws() {
        when(chapterRepository.findById("missing")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> chapterRecapService.saveGeneratedRecap("missing", null, "v1", "grok")
        );
        assertTrue(ex.getMessage().contains("Chapter not found"));
    }

    @Test
    void requestChapterRecap_existingPendingRecap_requeuesAsPending() {
        ChapterEntity chapter = createChapter("book-1", "chapter-1", 1, "Chapter 1");
        ChapterRecapEntity recap = new ChapterRecapEntity(chapter);
        recap.setStatus(ChapterRecapStatus.FAILED);

        when(chapterRecapRepository.findByChapterId("chapter-1")).thenReturn(Optional.of(recap));

        chapterRecapService.requestChapterRecap("chapter-1");

        ArgumentCaptor<ChapterRecapEntity> captor = ArgumentCaptor.forClass(ChapterRecapEntity.class);
        verify(chapterRecapRepository).save(captor.capture());
        assertEquals(ChapterRecapStatus.PENDING, captor.getValue().getStatus());
    }

    @Test
    void processChapterRecap_withAvailableProvider_persistsLlmGeneratedPayload() {
        ReflectionTestUtils.setField(chapterRecapService, "maxContextChars", 4000);
        when(reasoningProvider.isAvailable()).thenReturn(true);
        when(reasoningProvider.getProviderName()).thenReturn("xai");
        when(reasoningProvider.generate(any(), any())).thenReturn("""
                {
                  "shortSummary": "Holmes explains the case.",
                  "keyEvents": ["A clue is revealed.", "Watson notes the change in mood."],
                  "characterDeltas": [{"characterName": "Holmes", "delta": "Takes control of the investigation."}]
                }
                """);

        ChapterEntity chapter = createChapter("book-1", "chapter-1", 1, "Chapter 1");
        when(chapterRepository.findByIdWithBook("chapter-1")).thenReturn(Optional.of(chapter));
        when(chapterRepository.findById("chapter-1")).thenReturn(Optional.of(chapter));
        when(chapterRecapRepository.findByChapterId("chapter-1")).thenReturn(Optional.empty());
        when(chapterRecapRepository.save(any(ChapterRecapEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ParagraphEntity paragraph = new ParagraphEntity(0, "Holmes studies the clue and explains his reasoning.");
        when(paragraphRepository.findByChapterIdOrderByParagraphIndex("chapter-1")).thenReturn(List.of(paragraph));
        when(characterRepository.findByBookIdUpToChapter("book-1", 1)).thenReturn(List.of());

        ReflectionTestUtils.invokeMethod(chapterRecapService, "processChapterRecap", "chapter-1");

        ArgumentCaptor<ChapterRecapEntity> captor = ArgumentCaptor.forClass(ChapterRecapEntity.class);
        verify(chapterRecapRepository).save(captor.capture());
        ChapterRecapEntity saved = captor.getValue();
        assertEquals(ChapterRecapStatus.COMPLETED, saved.getStatus());
        assertEquals("v2-llm-json", saved.getPromptVersion());
        assertEquals("xai", saved.getModelName());
        assertNotNull(saved.getPayloadJson());
        assertTrue(saved.getPayloadJson().contains("Holmes explains the case."));
    }

    private ChapterEntity createChapter(String bookId, String chapterId, int index, String title) {
        BookEntity book = new BookEntity("Title", "Author", "gutenberg");
        book.setId(bookId);

        ChapterEntity chapter = new ChapterEntity(index, title);
        chapter.setId(chapterId);
        chapter.setBook(book);
        return chapter;
    }
}
