package org.example.reader.config;

import org.junit.jupiter.api.Test;

import static org.example.reader.config.SensitiveApiRequestMatcher.EndpointType.CHAT;
import static org.example.reader.config.SensitiveApiRequestMatcher.EndpointType.GENERATION;
import static org.example.reader.config.SensitiveApiRequestMatcher.EndpointType.NONE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SensitiveApiRequestMatcherTest {

    @Test
    void classify_marksGenerationEndpoints() {
        assertEquals(GENERATION, SensitiveApiRequestMatcher.classify("POST", "/api/pregen/book/book-1"));
        assertEquals(GENERATION, SensitiveApiRequestMatcher.classify("POST", "/api/illustrations/chapter/ch-1/request"));
        assertEquals(GENERATION, SensitiveApiRequestMatcher.classify("POST", "/api/quizzes/chapter/ch-1/generate"));
        assertEquals(GENERATION, SensitiveApiRequestMatcher.classify("GET", "/api/tts/speak/book-1/chapter-2/3"));
    }

    @Test
    void classify_marksChatEndpoints() {
        assertEquals(CHAT, SensitiveApiRequestMatcher.classify("POST", "/api/characters/char-1/chat"));
        assertEquals(CHAT, SensitiveApiRequestMatcher.classify("POST", "/api/recaps/book/book-1/chat"));
    }

    @Test
    void classify_ignoresNonSensitiveEndpoints() {
        assertEquals(NONE, SensitiveApiRequestMatcher.classify("GET", "/api/import/popular"));
        assertEquals(NONE, SensitiveApiRequestMatcher.classify("POST", "/api/recaps/analytics"));
        assertEquals(NONE, SensitiveApiRequestMatcher.classify(null, "/api/pregen/book/book-1"));
    }
}
