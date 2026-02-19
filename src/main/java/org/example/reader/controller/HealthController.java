package org.example.reader.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.reader.config.RequestCorrelation;
import org.example.reader.model.GenerationJobStatusResponse;
import org.example.reader.service.AccountAuthService;
import org.example.reader.service.AccountMetricsService;
import org.example.reader.service.ChapterQuizService;
import org.example.reader.service.ChapterRecapChatService;
import org.example.reader.service.ChapterRecapService;
import org.example.reader.service.CharacterChatService;
import org.example.reader.service.CharacterExtractionService;
import org.example.reader.service.CharacterService;
import org.example.reader.service.ComfyUIService;
import org.example.reader.service.GenerationJobStatusService;
import org.example.reader.service.IllustrationService;
import org.example.reader.service.IllustrationStyleAnalysisService;
import org.example.reader.service.QuizMetricsService;
import org.example.reader.service.RecapMetricsService;
import org.example.reader.service.TtsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final GenerationJobStatusService generationJobStatusService;
    private final AccountAuthService accountAuthService;
    private final AccountMetricsService accountMetricsService;
    private final ChapterQuizService chapterQuizService;
    private final ChapterRecapService chapterRecapService;
    private final ChapterRecapChatService chapterRecapChatService;
    private final CharacterService characterService;
    private final CharacterExtractionService characterExtractionService;
    private final CharacterChatService characterChatService;
    private final IllustrationService illustrationService;
    private final IllustrationStyleAnalysisService illustrationStyleAnalysisService;
    private final ComfyUIService comfyUIService;
    private final TtsService ttsService;
    private final QuizMetricsService quizMetricsService;
    private final RecapMetricsService recapMetricsService;

    public HealthController(
            GenerationJobStatusService generationJobStatusService,
            AccountAuthService accountAuthService,
            AccountMetricsService accountMetricsService,
            ChapterQuizService chapterQuizService,
            ChapterRecapService chapterRecapService,
            ChapterRecapChatService chapterRecapChatService,
            CharacterService characterService,
            CharacterExtractionService characterExtractionService,
            CharacterChatService characterChatService,
            IllustrationService illustrationService,
            IllustrationStyleAnalysisService illustrationStyleAnalysisService,
            ComfyUIService comfyUIService,
            TtsService ttsService,
            QuizMetricsService quizMetricsService,
            RecapMetricsService recapMetricsService) {
        this.generationJobStatusService = generationJobStatusService;
        this.accountAuthService = accountAuthService;
        this.accountMetricsService = accountMetricsService;
        this.chapterQuizService = chapterQuizService;
        this.chapterRecapService = chapterRecapService;
        this.chapterRecapChatService = chapterRecapChatService;
        this.characterService = characterService;
        this.characterExtractionService = characterExtractionService;
        this.characterChatService = characterChatService;
        this.illustrationService = illustrationService;
        this.illustrationStyleAnalysisService = illustrationStyleAnalysisService;
        this.comfyUIService = comfyUIService;
        this.ttsService = ttsService;
        this.quizMetricsService = quizMetricsService;
        this.recapMetricsService = recapMetricsService;
    }

    @GetMapping("/health")
    public Health health() {
        return new Health("ok");
    }

    @GetMapping("/health/details")
    public HealthDetails healthDetails(HttpServletRequest request) {
        GenerationJobStatusResponse generationStatus = generationJobStatusService.getGlobalStatus();
        ProviderHealth providers = new ProviderHealth(
                chapterQuizService.isProviderAvailable(),
                chapterRecapService.isProviderAvailable(),
                chapterRecapChatService.isChatProviderAvailable(),
                characterExtractionService.isReasoningProviderAvailable(),
                characterChatService.isChatProviderAvailable(),
                illustrationStyleAnalysisService.isReasoningProviderAvailable(),
                comfyUIService.isAvailable(),
                ttsService.isConfigured()
        );
        QueueHealth queues = new QueueHealth(
                illustrationService.isQueueProcessorRunning(),
                characterService.isQueueProcessorRunning(),
                chapterRecapService.isQueueProcessorRunning(),
                chapterQuizService.isQueueProcessorRunning(),
                illustrationService.getQueueDepth(),
                characterService.getQueueDepth(),
                chapterRecapService.getQueueDepth(),
                chapterQuizService.getQueueDepth(),
                generationStatus
        );

        boolean queueProcessorsHealthy = queues.illustrationQueueProcessorRunning()
                && queues.characterQueueProcessorRunning()
                && queues.recapQueueProcessorRunning()
                && queues.quizQueueProcessorRunning();

        Map<String, Object> accountMetrics = new LinkedHashMap<>(accountMetricsService.snapshot());
        accountMetrics.put("accountAuthEnabled", accountAuthService.isAccountAuthEnabled());
        accountMetrics.put("rolloutMode", accountAuthService.getRolloutMode());
        accountMetrics.put("accountRequired", accountAuthService.isAccountRequired());

        return new HealthDetails(
                queueProcessorsHealthy ? "ok" : "degraded",
                queueProcessorsHealthy,
                RequestCorrelation.resolveRequestId(request),
                LocalDateTime.now(),
                providers,
                queues,
                quizMetricsService.snapshot(),
                recapMetricsService.snapshot(),
                accountMetrics
        );
    }

    public record Health(String status) {}

    public record HealthDetails(
            String status,
            boolean queueProcessorsHealthy,
            String requestId,
            LocalDateTime asOf,
            ProviderHealth providers,
            QueueHealth queues,
            Map<String, Object> quizMetrics,
            Map<String, Object> recapMetrics,
            Map<String, Object> accountMetrics
    ) {
    }

    public record ProviderHealth(
            boolean quizReasoningAvailable,
            boolean recapReasoningAvailable,
            boolean recapChatAvailable,
            boolean characterReasoningAvailable,
            boolean characterChatAvailable,
            boolean illustrationReasoningAvailable,
            boolean comfyUiAvailable,
            boolean ttsConfigured
    ) {
    }

    public record QueueHealth(
            boolean illustrationQueueProcessorRunning,
            boolean characterQueueProcessorRunning,
            boolean recapQueueProcessorRunning,
            boolean quizQueueProcessorRunning,
            int illustrationQueueDepth,
            int characterQueueDepth,
            int recapQueueDepth,
            int quizQueueDepth,
            GenerationJobStatusResponse generation
    ) {
    }
}
