package org.example.reader.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CharacterExtractionService {

    private static final Logger log = LoggerFactory.getLogger(CharacterExtractionService.class);

    @Value("${ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${ollama.model}")
    private String ollamaModel;

    @Value("${ollama.timeout-seconds:180}")
    private int timeoutSeconds;

    @Value("${character.extraction.max-characters-per-chapter:5}")
    private int maxCharactersPerChapter;

    private WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record ExtractedCharacter(
        String name,
        String description,
        int approximateParagraphIndex
    ) {}

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .baseUrl(ollamaBaseUrl)
                .build();
        log.info("Character extraction service initialized with model: {}", ollamaModel);
    }

    public boolean isOllamaAvailable() {
        try {
            webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(2));
            return true;
        } catch (Exception e) {
            log.debug("Ollama not available: {}", e.getMessage());
            return false;
        }
    }

    public List<ExtractedCharacter> extractCharactersFromChapter(
            String bookTitle,
            String author,
            String chapterTitle,
            String chapterContent,
            List<String> existingCharacterNames) {

        String existingCharactersList = existingCharacterNames.isEmpty()
                ? "(none yet)"
                : existingCharacterNames.stream()
                    .map(name -> "- " + name)
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("(none yet)");

        String prompt = String.format("""
            Analyze this chapter and identify any NEW characters that are introduced.
            A character is someone who:
            - Has a name (or a clear designation like "the old woman" or "the stranger")
            - Appears as a distinct person in the narrative
            - Has at least one line of dialogue OR plays a meaningful role in the scene
            - Is NOT already in the known characters list below

            Book: %s by %s
            Chapter: %s

            Already known characters (DO NOT include these):
            %s

            Chapter content:
            ---
            %s
            ---

            IMPORTANT:
            - Only include genuinely NEW characters not in the list above
            - Maximum %d new characters per chapter
            - Do not include historical figures mentioned in passing
            - Do not include groups of people (e.g., "the crowd") unless one individual stands out
            - Estimate which paragraph (0-indexed) the character first appears in

            Respond with ONLY valid JSON array, no other text:
            [
              {
                "name": "Character Name",
                "description": "2-3 sentence description of who they are, their role, and notable traits",
                "approximateParagraphIndex": 5
              }
            ]

            If no new characters are introduced, respond with: []
            """,
                bookTitle, author, chapterTitle,
                existingCharactersList,
                truncateText(chapterContent, 3000),
                maxCharactersPerChapter);

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", ollamaModel,
                    "prompt", prompt,
                    "stream", false,
                    "options", Map.of(
                            "temperature", 0.3
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
            String generatedText = responseNode.get("response").asText();

            String json = extractJsonArray(generatedText);
            JsonNode charactersArray = objectMapper.readTree(json);

            List<ExtractedCharacter> characters = new ArrayList<>();
            for (JsonNode charNode : charactersArray) {
                String name = charNode.get("name").asText();
                String description = charNode.has("description")
                        ? charNode.get("description").asText()
                        : "A character in the story";
                int paragraphIndex = charNode.has("approximateParagraphIndex")
                        ? charNode.get("approximateParagraphIndex").asInt(0)
                        : 0;

                // Skip if name matches existing character (case-insensitive)
                boolean isDuplicate = existingCharacterNames.stream()
                        .anyMatch(existing -> existing.equalsIgnoreCase(name));
                if (!isDuplicate && !name.isBlank()) {
                    characters.add(new ExtractedCharacter(name, description, paragraphIndex));
                }
            }

            log.info("Extracted {} new characters from chapter '{}'", characters.size(), chapterTitle);
            return characters;

        } catch (WebClientResponseException e) {
            log.error("Ollama API error during character extraction: {} - {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.error("Failed to extract characters from chapter '{}'", chapterTitle, e);
            return List.of();
        }
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    private String extractJsonArray(String text) {
        // Find JSON array in the response
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        // If no array found, check if it's an empty response
        if (text.trim().equalsIgnoreCase("[]") || text.contains("no new characters")) {
            return "[]";
        }
        log.warn("No JSON array found in response, returning empty: {}", text);
        return "[]";
    }
}
