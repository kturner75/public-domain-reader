package org.example.reader.controller;

import org.example.reader.entity.BookEntity;
import org.example.reader.entity.ChapterEntity;
import org.example.reader.entity.ParagraphEntity;
import org.example.reader.repository.BookRepository;
import org.example.reader.repository.ChapterRepository;
import org.example.reader.repository.ParagraphRepository;
import org.example.reader.service.AssetKeyService;
import org.example.reader.service.CdnAssetService;
import org.example.reader.service.TtsService;
import org.example.reader.service.VoiceAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TtsController.class)
class TtsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TtsService ttsService;

    @MockitoBean
    private VoiceAnalysisService voiceAnalysisService;

    @MockitoBean
    private BookRepository bookRepository;

    @MockitoBean
    private ChapterRepository chapterRepository;

    @MockitoBean
    private ParagraphRepository paragraphRepository;

    @MockitoBean
    private AssetKeyService assetKeyService;

    @MockitoBean
    private CdnAssetService cdnAssetService;

    @Test
    void getStatus_cacheOnlyWithCdn_setsCachedAvailable() throws Exception {
        when(ttsService.isCacheOnly()).thenReturn(true);
        when(ttsService.isConfigured()).thenReturn(false);
        when(voiceAnalysisService.isOllamaAvailable()).thenReturn(true);
        when(cdnAssetService.isEnabled()).thenReturn(true);

        mockMvc.perform(get("/api/tts/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cacheOnly", is(true)))
                .andExpect(jsonPath("$.openaiConfigured", is(false)))
                .andExpect(jsonPath("$.cachedAvailable", is(true)))
                .andExpect(jsonPath("$.ollamaAvailable", is(true)))
                .andExpect(jsonPath("$.voices[0].id", is("ash")));
    }

    @Test
    void analyzeBook_existingSettingsWithoutForce_returnsSavedSettings() throws Exception {
        BookEntity book = new BookEntity("Book One", "Author One", "gutenberg");
        book.setId("book-1");
        book.setTtsEnabled(true);
        book.setTtsVoice("fable");
        book.setTtsSpeed(1.15);
        book.setTtsInstructions("Speak warmly");
        book.setTtsReasoning("Matches tone");

        when(bookRepository.findById("book-1")).thenReturn(Optional.of(book));
        when(ttsService.isCacheOnly()).thenReturn(false);

        mockMvc.perform(post("/api/tts/analyze/book-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voice", is("fable")))
                .andExpect(jsonPath("$.speed", is(1.15)))
                .andExpect(jsonPath("$.instructions", is("Speak warmly")))
                .andExpect(jsonPath("$.reasoning", is("Matches tone")));

        verify(bookRepository, never()).save(org.mockito.ArgumentMatchers.any(BookEntity.class));
        verify(voiceAnalysisService, never()).analyzeBookForVoice(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void speak_whenNotConfigured_returnsServiceUnavailable() throws Exception {
        when(ttsService.isConfigured()).thenReturn(false);

        mockMvc.perform(post("/api/tts/speak")
                        .contentType("application/json")
                        .content("""
                                {
                                  "text": "Hello world",
                                  "voice": "fable",
                                  "speed": 1.0,
                                  "instructions": ""
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("OpenAI API key not configured"));
    }

    @Test
    void speakParagraph_cacheOnlyWithCdn_redirectsToCachedAsset() throws Exception {
        BookEntity book = new BookEntity("Book One", "Author One", "gutenberg");
        book.setId("book-1");
        book.setTtsEnabled(true);

        ChapterEntity chapter = new ChapterEntity(2, "Chapter Three");
        chapter.setId("chapter-1");
        chapter.setBook(book);

        ParagraphEntity paragraph = new ParagraphEntity();
        paragraph.setContent("<p>Hello from paragraph.</p>");

        when(bookRepository.findById("book-1")).thenReturn(Optional.of(book));
        when(chapterRepository.findById("chapter-1")).thenReturn(Optional.of(chapter));
        when(paragraphRepository.findByChapterIdOrderByParagraphIndex("chapter-1")).thenReturn(List.of(paragraph));
        when(assetKeyService.buildBookKey(book)).thenReturn("book-one");
        when(ttsService.isCacheOnly()).thenReturn(true);
        when(cdnAssetService.isEnabled()).thenReturn(true);
        when(ttsService.resolveVoice("fable")).thenReturn("fable");
        when(assetKeyService.buildAudioKey(book, "fable", 2, 0)).thenReturn("audio-key");
        when(cdnAssetService.buildAssetUrl("audio", "audio-key"))
                .thenReturn(Optional.of("https://cdn.example.com/audio-key.mp3"));

        mockMvc.perform(get("/api/tts/speak/book-1/chapter-1/0").param("voice", "fable"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://cdn.example.com/audio-key.mp3"));

        verify(ttsService, never()).generateSpeechForParagraph(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }
}
