package org.example.reader.service;

import org.example.reader.repository.CharacterRepository;
import org.example.reader.repository.ChapterRepository;
import org.example.reader.service.llm.LlmProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CharacterChatServiceTest {

    @Mock
    private LlmProvider llmProvider;

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private ChapterRepository chapterRepository;

    private CharacterChatService characterChatService;

    @BeforeEach
    void setUp() {
        when(llmProvider.getProviderName()).thenReturn("test-provider");
        characterChatService = new CharacterChatService(llmProvider, characterRepository, chapterRepository);
    }

    @Test
    void chat_cacheOnlyMode_returnsCacheOnlyMessage() {
        ReflectionTestUtils.setField(characterChatService, "cacheOnly", true);

        String response = characterChatService.chat("character-1", "Hello", List.of(), 0, 0);

        assertEquals("Character chat is unavailable in cache-only mode.", response);
        verify(llmProvider, never()).generate(anyString(), any());
    }
}
