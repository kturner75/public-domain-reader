package org.example.reader.controller;

import org.example.reader.model.ChatMessage;
import org.example.reader.model.ChapterRecapResponse;
import org.example.reader.model.ChapterRecapStatusResponse;
import org.example.reader.service.ChapterRecapChatService;
import org.example.reader.service.ChapterRecapService;
import org.example.reader.service.RecapMetricsService;
import org.example.reader.service.RecapRolloutService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recaps")
public class ChapterRecapController {

    @Value("${recap.enabled:true}")
    private boolean recapEnabled;

    @Value("${ai.reasoning.enabled:true}")
    private boolean reasoningEnabled;

    @Value("${ai.chat.enabled:false}")
    private boolean chatEnabled;

    @Value("${generation.cache-only:false}")
    private boolean cacheOnly;

    private final ChapterRecapService chapterRecapService;
    private final ChapterRecapChatService chapterRecapChatService;
    private final RecapRolloutService recapRolloutService;
    private final RecapMetricsService recapMetricsService;

    public ChapterRecapController(
            ChapterRecapService chapterRecapService,
            ChapterRecapChatService chapterRecapChatService,
            RecapRolloutService recapRolloutService,
            RecapMetricsService recapMetricsService) {
        this.chapterRecapService = chapterRecapService;
        this.chapterRecapChatService = chapterRecapChatService;
        this.recapRolloutService = recapRolloutService;
        this.recapMetricsService = recapMetricsService;
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", recapEnabled);
        status.put("reasoningEnabled", reasoningEnabled);
        status.put("chatEnabled", chatEnabled);
        status.put("cacheOnly", cacheOnly);
        status.put("chatProviderAvailable", chapterRecapChatService.isChatProviderAvailable());
        status.put("available", recapEnabled && reasoningEnabled);
        status.put("rolloutMode", recapRolloutService.getRolloutMode());
        status.put("rolloutAllowListSize", recapRolloutService.getAllowListSize());
        status.put("queueDepth", chapterRecapService.getQueueDepth());
        status.put("metrics", recapMetricsService.snapshot());
        return status;
    }

    @GetMapping("/book/{bookId}/status")
    public Map<String, Object> getBookStatus(@PathVariable String bookId) {
        boolean rolloutAllowed = recapRolloutService.isBookAllowed(bookId);
        boolean available = recapEnabled && reasoningEnabled && rolloutAllowed;

        Map<String, Object> status = new HashMap<>();
        status.put("enabled", recapEnabled);
        status.put("reasoningEnabled", reasoningEnabled);
        status.put("chatEnabled", chatEnabled);
        status.put("cacheOnly", cacheOnly);
        status.put("chatProviderAvailable", chapterRecapChatService.isChatProviderAvailable());
        status.put("rolloutMode", recapRolloutService.getRolloutMode());
        status.put("rolloutAllowed", rolloutAllowed);
        status.put("available", available);
        return status;
    }

    @GetMapping("/chapter/{chapterId}")
    public ResponseEntity<ChapterRecapResponse> getChapterRecap(@PathVariable String chapterId) {
        if (!recapEnabled) {
            return ResponseEntity.status(403).build();
        }
        var bookId = chapterRecapService.findBookIdForChapter(chapterId).orElse(null);
        if (bookId == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isBookAvailableForRecap(bookId)) {
            return ResponseEntity.status(403).build();
        }
        return chapterRecapService.getChapterRecap(chapterId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/chapter/{chapterId}/status")
    public ResponseEntity<ChapterRecapStatusResponse> getChapterRecapStatus(@PathVariable String chapterId) {
        if (!recapEnabled) {
            return ResponseEntity.status(403).build();
        }
        var bookId = chapterRecapService.findBookIdForChapter(chapterId).orElse(null);
        if (bookId == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isBookAvailableForRecap(bookId)) {
            return ResponseEntity.status(403).build();
        }
        return chapterRecapService.getChapterRecapStatus(chapterId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/chapter/{chapterId}/generate")
    public ResponseEntity<Void> requestChapterRecapGeneration(@PathVariable String chapterId) {
        if (!isRecapGenerationFeatureEnabled()) {
            return ResponseEntity.status(403).build();
        }
        if (cacheOnly) {
            return ResponseEntity.status(409).build();
        }
        var bookId = chapterRecapService.findBookIdForChapter(chapterId).orElse(null);
        if (bookId == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            chapterRecapService.requestChapterRecap(chapterId);
            return ResponseEntity.accepted().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/book/{bookId}/requeue-stuck")
    public ResponseEntity<RequeueStuckResponse> requeueStuckRecaps(@PathVariable String bookId) {
        if (!isRecapGenerationFeatureEnabled()) {
            return ResponseEntity.status(403).build();
        }
        if (cacheOnly) {
            return ResponseEntity.status(409).build();
        }
        int requeued = chapterRecapService.resetAndRequeueStuckForBook(bookId);
        return ResponseEntity.accepted().body(new RequeueStuckResponse(bookId, requeued));
    }

    @PostMapping("/book/{bookId}/chat")
    public ResponseEntity<RecapChatResponse> chat(
            @PathVariable String bookId,
            @RequestBody RecapChatRequest request) {
        if (!isBookAvailableForRecap(bookId)) {
            return ResponseEntity.status(403).build();
        }
        if (!chatEnabled) {
            recapMetricsService.recordChatRejected();
            return ResponseEntity.status(403).body(new RecapChatResponse(
                    "Chat is disabled in this environment.",
                    bookId,
                    System.currentTimeMillis()
            ));
        }
        if (request == null || request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        recapMetricsService.recordChatRequest();
        String response;
        try {
            response = chapterRecapChatService.chat(
                    bookId,
                    request.message(),
                    request.conversationHistory(),
                    request.readerChapterIndex()
            );
        } catch (Exception e) {
            recapMetricsService.recordChatFailed();
            response = "I can't answer right now, but you can continue reading and ask again.";
        }

        return ResponseEntity.ok(new RecapChatResponse(
                response,
                bookId,
                System.currentTimeMillis()
        ));
    }

    @PostMapping("/analytics")
    public ResponseEntity<Void> trackAnalytics(@RequestBody RecapAnalyticsRequest request) {
        if (request == null || request.event() == null || request.event().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.bookId() != null && !request.bookId().isBlank() && !isBookAvailableForRecap(request.bookId())) {
            return ResponseEntity.status(403).build();
        }

        switch (request.event().trim().toLowerCase()) {
            case "viewed" -> recapMetricsService.recordModalViewed();
            case "skipped" -> recapMetricsService.recordModalSkipped();
            case "continued" -> recapMetricsService.recordModalContinued();
            default -> {
                return ResponseEntity.badRequest().build();
            }
        }
        return ResponseEntity.accepted().build();
    }

    private boolean isBookAvailableForRecap(String bookId) {
        return recapEnabled && reasoningEnabled && recapRolloutService.isBookAllowed(bookId);
    }

    private boolean isRecapGenerationFeatureEnabled() {
        return recapEnabled && reasoningEnabled;
    }

    public record RecapChatRequest(
            String message,
            List<ChatMessage> conversationHistory,
            int readerChapterIndex
    ) {
    }

    public record RecapChatResponse(
            String response,
            String bookId,
            long timestamp
    ) {
    }

    public record RecapAnalyticsRequest(
            String bookId,
            String chapterId,
            String event
    ) {
    }

    public record RequeueStuckResponse(
            String bookId,
            int requeued
    ) {
    }
}
