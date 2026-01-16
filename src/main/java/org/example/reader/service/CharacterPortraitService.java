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
public class CharacterPortraitService {

    private static final Logger log = LoggerFactory.getLogger(CharacterPortraitService.class);

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
        log.info("Character portrait service initialized");
    }

    public String generatePortraitPrompt(
            String bookTitle,
            String author,
            String characterName,
            String characterDescription,
            IllustrationSettings bookStyle) {

        String settingContext = bookStyle.setting() != null
                ? "Cultural/Geographic Setting: " + bookStyle.setting()
                : "";

        String prompt = String.format("""
            Generate an image prompt for a character portrait in the style of a classic book illustration.

            Book: %s by %s
            Character: %s
            Character Description: %s
            Visual Style: %s
            %s

            Create a portrait prompt that:
            - Captures the character's essence, personality, and social standing
            - Uses the artistic style: %s
            - Shows the character from chest/shoulders up, in a 3/4 view or profile
            - Includes period-appropriate clothing, hair, and accessories for the book's setting
            - Has an atmospheric, painterly quality suitable for a book illustration
            - Suggests mood through lighting and background elements
            - Does NOT show a direct frontal face view
            - Maintains cultural accuracy for the book's setting

            Start your prompt with this style prefix: %s

            Respond with ONLY the image prompt, 50-100 words, no explanation.
            """,
                bookTitle,
                author,
                characterName,
                characterDescription,
                bookStyle.style(),
                settingContext,
                bookStyle.style(),
                bookStyle.promptPrefix());

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

            generatedPrompt = cleanPrompt(generatedPrompt);

            log.info("Generated portrait prompt for '{}': {}", characterName,
                    truncateText(generatedPrompt, 100));

            return generatedPrompt;

        } catch (Exception e) {
            log.error("Failed to generate portrait prompt for character: {}", characterName, e);
            return buildFallbackPrompt(characterName, characterDescription, bookStyle);
        }
    }

    private String buildFallbackPrompt(String characterName, String description,
                                       IllustrationSettings bookStyle) {
        return bookStyle.promptPrefix() + " portrait of " + characterName +
                ", " + truncateText(description, 50) +
                ", atmospheric book illustration, 3/4 view, period clothing";
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    private String cleanPrompt(String prompt) {
        if (prompt.startsWith("\"") && prompt.endsWith("\"")) {
            prompt = prompt.substring(1, prompt.length() - 1);
        }
        if (prompt.toLowerCase().startsWith("prompt:")) {
            prompt = prompt.substring(7).trim();
        }
        return prompt.trim();
    }
}
