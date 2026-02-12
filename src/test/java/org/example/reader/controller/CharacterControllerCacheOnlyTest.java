package org.example.reader.controller;

import org.example.reader.repository.BookRepository;
import org.example.reader.repository.ChapterRepository;
import org.example.reader.service.CdnAssetService;
import org.example.reader.service.CharacterChatService;
import org.example.reader.service.CharacterExtractionService;
import org.example.reader.service.CharacterPrefetchService;
import org.example.reader.service.CharacterService;
import org.example.reader.service.ComfyUIService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CharacterController.class)
@TestPropertySource(properties = {
        "generation.cache-only=true",
        "character.enabled=true",
        "ai.chat.enabled=true"
})
class CharacterControllerCacheOnlyTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CharacterService characterService;

    @MockitoBean
    private CharacterChatService chatService;

    @MockitoBean
    private CharacterExtractionService extractionService;

    @MockitoBean
    private CharacterPrefetchService prefetchService;

    @MockitoBean
    private ComfyUIService comfyUIService;

    @MockitoBean
    private CdnAssetService cdnAssetService;

    @MockitoBean
    private BookRepository bookRepository;

    @MockitoBean
    private ChapterRepository chapterRepository;

    @Test
    void getStatus_cacheOnlyMode_marksCharacterChatDisabled() throws Exception {
        mockMvc.perform(get("/api/characters/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cacheOnly", is(true)))
                .andExpect(jsonPath("$.chatEnabled", is(false)));
    }

    @Test
    void chat_cacheOnlyMode_returnsConflict() throws Exception {
        mockMvc.perform(post("/api/characters/character-1/chat")
                        .contentType("application/json")
                        .content("""
                                {
                                  "message": "Hello there",
                                  "conversationHistory": [],
                                  "readerChapterIndex": 0,
                                  "readerParagraphIndex": 0
                                }
                                """))
                .andExpect(status().isConflict());

        verifyNoInteractions(characterService);
        verifyNoInteractions(chatService);
    }
}
