package org.example.reader.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.example.reader.model.IllustrationSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

@Service
public class IllustrationPromptService {

    private static final Logger log = LoggerFactory.getLogger(IllustrationPromptService.class);

    @Value("${ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${ollama.model}")
    private String ollamaModel;

    @Value("${ollama.timeout-seconds:180}")
    private int timeoutSeconds;

    private WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .baseUrl(ollamaBaseUrl)
                .build();
        log.info("Illustration prompt service initialized");
    }

    /**
     * Generate an image prompt for a chapter based on its content.
     *
     * @param bookTitle The book title
     * @param author The author
     * @param chapterTitle The chapter title
     * @param chapterContent The chapter text (will be truncated)
     * @param styleSettings The illustration style settings for this book
     * @return A prompt suitable for image generation
     */
    public String generatePromptForChapter(
            String bookTitle,
            String author,
            String chapterTitle,
            String chapterContent,
            IllustrationSettings styleSettings) {

        String settingContext = styleSettings.setting() != null
                ? "Cultural/Geographic Setting: " + styleSettings.setting()
                : "";

        String prompt = String.format("""
            You are creating a prompt for an AI image generator to illustrate a chapter from a classic book.

            Book: %s by %s
            Chapter: %s
            Illustration Style: %s
            %s

            Chapter content excerpt:
            ---
            %s
            ---

            Generate a single, detailed image prompt that captures the essence of this chapter.

            CRITICAL REQUIREMENTS FOR CULTURAL ACCURACY:
            - The illustration MUST accurately reflect the book's specific cultural and geographic setting
            - Architecture, clothing, religious symbols, and landscapes must match the setting exactly
            - For Russian literature: use Russian Orthodox churches (onion domes), Slavic architecture, Russian landscapes
            - For English literature: use appropriate English/British architecture, countryside, weather
            - For American literature: use regionally-appropriate American settings
            - NEVER mix cultural elements (e.g., no Buddhist temples in Russian novels, no pagodas in English countryside)

            OTHER GUIDELINES:
            - Focus on: setting, atmosphere, key objects, and mood
            - DO NOT include human faces or detailed character features (use silhouettes, back views, or distant figures)
            - Describe the scene as if it were a book illustration plate
            - Include lighting, time of day, weather if relevant
            - Keep it evocative and atmospheric rather than literal

            Start your prompt with this style prefix: %s

            Respond with ONLY the image prompt, no explanation or other text. The prompt should be 50-150 words.
            """,
                bookTitle,
                author,
                chapterTitle,
                styleSettings.style(),
                settingContext,
                truncateText(chapterContent, 2000),
                styleSettings.promptPrefix());

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", ollamaModel,
                    "prompt", prompt,
                    "stream", false,
                    "options", Map.of(
                            "temperature", 0.7
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
            String generatedPrompt = responseNode.get("response").asText().trim();

            // Clean up the prompt - remove any quotes or extra formatting
            generatedPrompt = cleanPrompt(generatedPrompt);

            log.info("Generated illustration prompt for chapter '{}': {}", chapterTitle,
                    truncateText(generatedPrompt, 100));

            return generatedPrompt;

        } catch (Exception e) {
            log.error("Failed to generate illustration prompt for chapter: {}", chapterTitle, e);
            // Return a fallback prompt using the style prefix
            return styleSettings.promptPrefix() + " a scene from " + bookTitle + " by " + author +
                    ", chapter " + chapterTitle + ", atmospheric book illustration";
        }
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    private String cleanPrompt(String prompt) {
        // Remove surrounding quotes if present
        if (prompt.startsWith("\"") && prompt.endsWith("\"")) {
            prompt = prompt.substring(1, prompt.length() - 1);
        }
        // Remove any "Prompt:" or similar prefixes
        if (prompt.toLowerCase().startsWith("prompt:")) {
            prompt = prompt.substring(7).trim();
        }
        return prompt.trim();
    }
}
