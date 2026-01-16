package org.example.reader.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.example.reader.entity.BookEntity;
import org.example.reader.entity.CharacterEntity;
import org.example.reader.entity.ChapterEntity;
import org.example.reader.model.ChatMessage;
import org.example.reader.repository.CharacterRepository;
import org.example.reader.repository.ChapterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CharacterChatService {

    private static final Logger log = LoggerFactory.getLogger(CharacterChatService.class);

    @Value("${ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${character.chat.model:llama3.1:latest}")
    private String chatModel;

    @Value("${ollama.timeout-seconds:180}")
    private int timeoutSeconds;

    @Value("${character.chat.max-context-messages:10}")
    private int maxContextMessages;

    private final CharacterRepository characterRepository;
    private final ChapterRepository chapterRepository;

    private WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CharacterChatService(CharacterRepository characterRepository,
                               ChapterRepository chapterRepository) {
        this.characterRepository = characterRepository;
        this.chapterRepository = chapterRepository;
    }

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .baseUrl(ollamaBaseUrl)
                .build();
        log.info("Character chat service initialized with model: {}", chatModel);
    }

    public String chat(String characterId, String userMessage,
                       List<ChatMessage> conversationHistory,
                       int readerChapterIndex, int readerParagraphIndex) {

        Optional<CharacterEntity> characterOpt = characterRepository.findByIdWithBookAndChapter(characterId);
        if (characterOpt.isEmpty()) {
            log.warn("Character not found for chat: {}", characterId);
            return "I'm sorry, I seem to have lost my place in the story...";
        }

        CharacterEntity character = characterOpt.get();
        BookEntity book = character.getBook();

        String chapterTitle = getChapterTitle(book.getId(), readerChapterIndex);

        String systemPrompt = buildSystemPrompt(character, book, readerChapterIndex,
                readerParagraphIndex, chapterTitle);

        String conversationContext = buildConversationContext(conversationHistory);

        String fullPrompt = String.format("""
            %s

            %s

            User: %s

            %s:""",
                systemPrompt,
                conversationContext,
                userMessage,
                character.getName());

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", chatModel,
                    "prompt", fullPrompt,
                    "stream", false,
                    "options", Map.of(
                            "temperature", 0.8,
                            "top_p", 0.9
                    )
            );

            String response = webClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            JsonNode responseNode = objectMapper.readTree(response);
            String generatedText = responseNode.get("response").asText().trim();

            generatedText = cleanResponse(generatedText, character.getName());

            log.debug("Generated chat response for '{}': {}", character.getName(),
                    truncateText(generatedText, 100));

            return generatedText;

        } catch (Exception e) {
            log.error("Failed to generate chat response for character '{}'", character.getName(), e);
            return "I... I'm not sure how to answer that. Perhaps we could discuss something else?";
        }
    }

    private String buildSystemPrompt(CharacterEntity character, BookEntity book,
                                     int chapterIndex, int paragraphIndex, String chapterTitle) {
        return String.format("""
            You are roleplaying as %s from "%s" by %s.

            CHARACTER DESCRIPTION:
            %s

            IMPORTANT STORY CONSTRAINTS:
            - The reader is currently at Chapter %d ("%s"), paragraph %d
            - You can ONLY discuss events that have happened UP TO this point in the story
            - You do NOT know anything that happens AFTER this point
            - If asked about future events, politely deflect by saying you don't know what will happen
            - Stay in character at all times - speak as %s would speak
            - Use vocabulary, mannerisms, and speech patterns appropriate to the character

            RESPONSE GUIDELINES:
            - Keep responses conversational and engaging, under 200 words
            - Show the character's personality through your responses
            - You may express opinions, feelings, and thoughts that the character would have
            - If the character wouldn't know something, say so in character
            - React emotionally to topics as the character would

            Remember: You ARE %s. Respond as they would, with their voice, their concerns, their worldview.""",
                character.getName(),
                book.getTitle(),
                book.getAuthor(),
                character.getDescription(),
                chapterIndex + 1,
                chapterTitle != null ? chapterTitle : "Chapter " + (chapterIndex + 1),
                paragraphIndex,
                character.getName(),
                character.getName());
    }

    private String buildConversationContext(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }

        List<ChatMessage> recentHistory = history.size() > maxContextMessages
                ? history.subList(history.size() - maxContextMessages, history.size())
                : history;

        StringBuilder context = new StringBuilder("PREVIOUS CONVERSATION:\n");
        for (ChatMessage msg : recentHistory) {
            String role = "user".equals(msg.role()) ? "User" : "Character";
            context.append(role).append(": ").append(msg.content()).append("\n\n");
        }

        return context.toString();
    }

    private String getChapterTitle(String bookId, int chapterIndex) {
        return chapterRepository.findByBookIdAndChapterIndex(bookId, chapterIndex)
                .map(ChapterEntity::getTitle)
                .orElse(null);
    }

    private String cleanResponse(String response, String characterName) {
        response = response.trim();

        if (response.startsWith(characterName + ":")) {
            response = response.substring(characterName.length() + 1).trim();
        }

        if (response.startsWith("\"") && response.endsWith("\"")) {
            response = response.substring(1, response.length() - 1);
        }

        return response;
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
