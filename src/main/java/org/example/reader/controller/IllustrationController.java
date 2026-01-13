package org.example.reader.controller;

import org.example.reader.entity.BookEntity;
import org.example.reader.entity.IllustrationStatus;
import org.example.reader.model.IllustrationSettings;
import org.example.reader.repository.BookRepository;
import org.example.reader.service.ComfyUIService;
import org.example.reader.service.IllustrationService;
import org.example.reader.service.IllustrationStyleAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/illustrations")
public class IllustrationController {

    private static final Logger log = LoggerFactory.getLogger(IllustrationController.class);

    @Value("${illustration.allow-prompt-editing:false}")
    private boolean allowPromptEditing;

    private final IllustrationService illustrationService;
    private final IllustrationStyleAnalysisService styleAnalysisService;
    private final ComfyUIService comfyUIService;
    private final BookRepository bookRepository;

    public IllustrationController(
            IllustrationService illustrationService,
            IllustrationStyleAnalysisService styleAnalysisService,
            ComfyUIService comfyUIService,
            BookRepository bookRepository) {
        this.illustrationService = illustrationService;
        this.styleAnalysisService = styleAnalysisService;
        this.comfyUIService = comfyUIService;
        this.bookRepository = bookRepository;
    }

    /**
     * Check if illustration services are available.
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("comfyuiAvailable", comfyUIService.isAvailable());
        status.put("ollamaAvailable", styleAnalysisService.isOllamaAvailable());
        status.put("allowPromptEditing", allowPromptEditing);
        status.put("queueProcessorRunning", illustrationService.isQueueProcessorRunning());
        return status;
    }

    /**
     * Get saved illustration style settings for a book.
     */
    @GetMapping("/settings/{bookId}")
    public ResponseEntity<IllustrationSettings> getStyleSettings(@PathVariable String bookId) {
        Optional<BookEntity> bookOpt = bookRepository.findById(bookId);
        if (bookOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        BookEntity book = bookOpt.get();

        if (book.getIllustrationStyle() != null) {
            IllustrationSettings settings = new IllustrationSettings(
                    book.getIllustrationStyle(),
                    book.getIllustrationPromptPrefix(),
                    book.getIllustrationStyleReasoning()
            );
            return ResponseEntity.ok(settings);
        }

        return ResponseEntity.noContent().build();
    }

    /**
     * Analyze book and determine illustration style.
     */
    @PostMapping("/analyze/{bookId}")
    public ResponseEntity<IllustrationSettings> analyzeBook(
            @PathVariable String bookId,
            @RequestParam(required = false, defaultValue = "false") boolean force) {

        Optional<BookEntity> bookOpt = bookRepository.findById(bookId);
        if (bookOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        IllustrationSettings settings = illustrationService.getOrAnalyzeBookStyle(bookId, force);

        log.info("Analyzed illustration style for book {}: {}",
                bookOpt.get().getTitle(), settings.style());

        return ResponseEntity.ok(settings);
    }

    /**
     * Get the illustration image for a chapter.
     */
    @GetMapping("/chapter/{chapterId}")
    public ResponseEntity<byte[]> getIllustration(@PathVariable String chapterId) {
        byte[] image = illustrationService.getIllustration(chapterId);
        if (image == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "image/png")
                .header(HttpHeaders.CACHE_CONTROL, "max-age=604800") // Cache for 1 week
                .body(image);
    }

    /**
     * Get the status of illustration generation for a chapter.
     */
    @GetMapping("/chapter/{chapterId}/status")
    public Map<String, Object> getChapterStatus(@PathVariable String chapterId) {
        IllustrationStatus status = illustrationService.getStatus(chapterId);

        Map<String, Object> response = new HashMap<>();
        response.put("chapterId", chapterId);
        response.put("status", status != null ? status.name() : "NOT_REQUESTED");
        response.put("ready", status == IllustrationStatus.COMPLETED);

        return response;
    }

    /**
     * Request illustration generation for a chapter.
     */
    @PostMapping("/chapter/{chapterId}/request")
    public ResponseEntity<Void> requestIllustration(@PathVariable String chapterId) {
        illustrationService.requestIllustration(chapterId);
        return ResponseEntity.accepted().build();
    }

    /**
     * Pre-fetch the next chapter's illustration.
     */
    @PostMapping("/chapter/{chapterId}/prefetch-next")
    public ResponseEntity<Void> prefetchNext(@PathVariable String chapterId) {
        illustrationService.prefetchNextChapter(chapterId);
        return ResponseEntity.accepted().build();
    }

    /**
     * Get the prompt used for an illustration.
     */
    @GetMapping("/chapter/{chapterId}/prompt")
    public ResponseEntity<Map<String, String>> getPrompt(@PathVariable String chapterId) {
        if (!allowPromptEditing) {
            return ResponseEntity.status(403).build();
        }

        String prompt = illustrationService.getPrompt(chapterId);
        if (prompt == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of("prompt", prompt));
    }

    /**
     * Regenerate illustration with a custom prompt.
     */
    @PostMapping("/chapter/{chapterId}/regenerate")
    public ResponseEntity<Void> regenerate(
            @PathVariable String chapterId,
            @RequestBody RegenerateRequest request) {

        if (!allowPromptEditing) {
            return ResponseEntity.status(403).build();
        }

        if (request.prompt() == null || request.prompt().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        illustrationService.regenerateWithPrompt(chapterId, request.prompt());
        return ResponseEntity.accepted().build();
    }

    /**
     * Retry stuck PENDING illustrations.
     */
    @PostMapping("/retry-stuck")
    public ResponseEntity<Void> retryStuck() {
        illustrationService.retryStuckPendingIllustrations();
        return ResponseEntity.accepted().build();
    }

    public record RegenerateRequest(String prompt) {}
}
