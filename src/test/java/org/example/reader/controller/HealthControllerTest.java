package org.example.reader.controller;

import org.example.reader.model.GenerationJobStatusResponse;
import org.example.reader.model.GenerationPipelineStatus;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GenerationJobStatusService generationJobStatusService;

    @MockitoBean
    private AccountAuthService accountAuthService;

    @MockitoBean
    private AccountMetricsService accountMetricsService;

    @MockitoBean
    private ChapterQuizService chapterQuizService;

    @MockitoBean
    private ChapterRecapService chapterRecapService;

    @MockitoBean
    private ChapterRecapChatService chapterRecapChatService;

    @MockitoBean
    private CharacterService characterService;

    @MockitoBean
    private CharacterExtractionService characterExtractionService;

    @MockitoBean
    private CharacterChatService characterChatService;

    @MockitoBean
    private IllustrationService illustrationService;

    @MockitoBean
    private IllustrationStyleAnalysisService illustrationStyleAnalysisService;

    @MockitoBean
    private ComfyUIService comfyUIService;

    @MockitoBean
    private TtsService ttsService;

    @MockitoBean
    private QuizMetricsService quizMetricsService;

    @MockitoBean
    private RecapMetricsService recapMetricsService;

    @Test
    void health_returnsBasicStatus() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")));
    }

    @Test
    void healthDetails_whenQueuesHealthy_returnsOkSnapshot() throws Exception {
        when(generationJobStatusService.getGlobalStatus()).thenReturn(sampleGenerationStatus());
        when(chapterQuizService.isProviderAvailable()).thenReturn(true);
        when(chapterRecapService.isProviderAvailable()).thenReturn(true);
        when(chapterRecapChatService.isChatProviderAvailable()).thenReturn(true);
        when(characterExtractionService.isReasoningProviderAvailable()).thenReturn(true);
        when(characterChatService.isChatProviderAvailable()).thenReturn(true);
        when(illustrationStyleAnalysisService.isReasoningProviderAvailable()).thenReturn(true);
        when(comfyUIService.isAvailable()).thenReturn(true);
        when(ttsService.isConfigured()).thenReturn(true);
        when(illustrationService.isQueueProcessorRunning()).thenReturn(true);
        when(characterService.isQueueProcessorRunning()).thenReturn(true);
        when(chapterRecapService.isQueueProcessorRunning()).thenReturn(true);
        when(chapterQuizService.isQueueProcessorRunning()).thenReturn(true);
        when(illustrationService.getQueueDepth()).thenReturn(1);
        when(characterService.getQueueDepth()).thenReturn(2);
        when(chapterRecapService.getQueueDepth()).thenReturn(3);
        when(chapterQuizService.getQueueDepth()).thenReturn(4);
        when(quizMetricsService.snapshot()).thenReturn(Map.of("readFailed", 0L, "statusReadFailed", 0L));
        when(recapMetricsService.snapshot()).thenReturn(Map.of("readFailed", 1L, "statusReadFailed", 2L));
        when(accountMetricsService.snapshot()).thenReturn(Map.of("claimSyncSucceeded", 5L));
        when(accountAuthService.isAccountAuthEnabled()).thenReturn(true);
        when(accountAuthService.getRolloutMode()).thenReturn("internal");
        when(accountAuthService.isAccountRequired()).thenReturn(false);

        mockMvc.perform(get("/health/details"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.status", is("ok")))
                .andExpect(jsonPath("$.queueProcessorsHealthy", is(true)))
                .andExpect(jsonPath("$.providers.quizReasoningAvailable", is(true)))
                .andExpect(jsonPath("$.providers.recapReasoningAvailable", is(true)))
                .andExpect(jsonPath("$.providers.comfyUiAvailable", is(true)))
                .andExpect(jsonPath("$.queues.quizQueueDepth", is(4)))
                .andExpect(jsonPath("$.queues.generation.scope", is("global")))
                .andExpect(jsonPath("$.quizMetrics.readFailed", is(0)))
                .andExpect(jsonPath("$.recapMetrics.statusReadFailed", is(2)))
                .andExpect(jsonPath("$.accountMetrics.claimSyncSucceeded", is(5)))
                .andExpect(jsonPath("$.accountMetrics.rolloutMode", is("internal")));
    }

    @Test
    void healthDetails_whenAnyQueueProcessorDown_returnsDegraded() throws Exception {
        when(generationJobStatusService.getGlobalStatus()).thenReturn(sampleGenerationStatus());
        when(chapterQuizService.isProviderAvailable()).thenReturn(true);
        when(chapterRecapService.isProviderAvailable()).thenReturn(true);
        when(chapterRecapChatService.isChatProviderAvailable()).thenReturn(true);
        when(characterExtractionService.isReasoningProviderAvailable()).thenReturn(true);
        when(characterChatService.isChatProviderAvailable()).thenReturn(true);
        when(illustrationStyleAnalysisService.isReasoningProviderAvailable()).thenReturn(true);
        when(comfyUIService.isAvailable()).thenReturn(true);
        when(ttsService.isConfigured()).thenReturn(true);
        when(illustrationService.isQueueProcessorRunning()).thenReturn(true);
        when(characterService.isQueueProcessorRunning()).thenReturn(false);
        when(chapterRecapService.isQueueProcessorRunning()).thenReturn(true);
        when(chapterQuizService.isQueueProcessorRunning()).thenReturn(true);
        when(illustrationService.getQueueDepth()).thenReturn(0);
        when(characterService.getQueueDepth()).thenReturn(0);
        when(chapterRecapService.getQueueDepth()).thenReturn(0);
        when(chapterQuizService.getQueueDepth()).thenReturn(0);
        when(quizMetricsService.snapshot()).thenReturn(Map.of("readFailed", 0L, "statusReadFailed", 0L));
        when(recapMetricsService.snapshot()).thenReturn(Map.of("readFailed", 0L, "statusReadFailed", 0L));
        when(accountMetricsService.snapshot()).thenReturn(Map.of());
        when(accountAuthService.isAccountAuthEnabled()).thenReturn(true);
        when(accountAuthService.getRolloutMode()).thenReturn("optional");
        when(accountAuthService.isAccountRequired()).thenReturn(false);

        mockMvc.perform(get("/health/details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("degraded")))
                .andExpect(jsonPath("$.queueProcessorsHealthy", is(false)))
                .andExpect(jsonPath("$.queues.characterQueueProcessorRunning", is(false)));
    }

    private GenerationJobStatusResponse sampleGenerationStatus() {
        GenerationPipelineStatus sample = GenerationPipelineStatus.of(1, 1, 1, 1, 1);
        return new GenerationJobStatusResponse(
                "global",
                null,
                LocalDateTime.of(2026, 2, 15, 10, 0),
                sample,
                sample,
                sample,
                sample,
                GenerationPipelineStatus.of(4, 4, 4, 4, 4)
        );
    }
}
