package org.example.reader.service;

import org.example.reader.entity.ChapterAnalysisStatus;
import org.example.reader.entity.ChapterRecapStatus;
import org.example.reader.entity.CharacterStatus;
import org.example.reader.entity.IllustrationStatus;
import org.example.reader.model.GenerationJobStatusResponse;
import org.example.reader.repository.ChapterAnalysisRepository;
import org.example.reader.repository.ChapterRecapRepository;
import org.example.reader.repository.CharacterRepository;
import org.example.reader.repository.IllustrationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerationJobStatusServiceTest {

    @Mock
    private IllustrationRepository illustrationRepository;

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private ChapterAnalysisRepository chapterAnalysisRepository;

    @Mock
    private ChapterRecapRepository chapterRecapRepository;

    @Test
    void getGlobalStatus_aggregatesPendingAndScheduledRetries() {
        when(illustrationRepository.countByStatus(IllustrationStatus.PENDING)).thenReturn(5L);
        when(illustrationRepository.countScheduledRetries(eq(IllustrationStatus.PENDING), any(LocalDateTime.class))).thenReturn(2L);
        when(illustrationRepository.countByStatus(IllustrationStatus.GENERATING)).thenReturn(1L);
        when(illustrationRepository.countByStatus(IllustrationStatus.COMPLETED)).thenReturn(7L);
        when(illustrationRepository.countByStatus(IllustrationStatus.FAILED)).thenReturn(3L);

        when(characterRepository.countByStatus(CharacterStatus.PENDING)).thenReturn(4L);
        when(characterRepository.countScheduledRetries(eq(CharacterStatus.PENDING), any(LocalDateTime.class))).thenReturn(1L);
        when(characterRepository.countByStatus(CharacterStatus.GENERATING)).thenReturn(2L);
        when(characterRepository.countByStatus(CharacterStatus.COMPLETED)).thenReturn(6L);
        when(characterRepository.countByStatus(CharacterStatus.FAILED)).thenReturn(1L);

        when(chapterAnalysisRepository.countByStatus(ChapterAnalysisStatus.PENDING)).thenReturn(3L);
        when(chapterAnalysisRepository.countByStatusIsNull()).thenReturn(2L);
        when(chapterAnalysisRepository.countScheduledRetries(eq(ChapterAnalysisStatus.PENDING), any(LocalDateTime.class))).thenReturn(1L);
        when(chapterAnalysisRepository.countByStatus(ChapterAnalysisStatus.GENERATING)).thenReturn(1L);
        when(chapterAnalysisRepository.countByStatus(ChapterAnalysisStatus.COMPLETED)).thenReturn(8L);
        when(chapterAnalysisRepository.countByStatus(ChapterAnalysisStatus.FAILED)).thenReturn(2L);

        when(chapterRecapRepository.countByStatus(ChapterRecapStatus.PENDING)).thenReturn(6L);
        when(chapterRecapRepository.countByStatusIsNull()).thenReturn(1L);
        when(chapterRecapRepository.countScheduledRetries(eq(ChapterRecapStatus.PENDING), any(LocalDateTime.class))).thenReturn(3L);
        when(chapterRecapRepository.countByStatus(ChapterRecapStatus.GENERATING)).thenReturn(2L);
        when(chapterRecapRepository.countByStatus(ChapterRecapStatus.COMPLETED)).thenReturn(9L);
        when(chapterRecapRepository.countByStatus(ChapterRecapStatus.FAILED)).thenReturn(4L);

        GenerationJobStatusService service = new GenerationJobStatusService(
                illustrationRepository,
                characterRepository,
                chapterAnalysisRepository,
                chapterRecapRepository
        );

        GenerationJobStatusResponse response = service.getGlobalStatus();

        assertEquals("global", response.scope());
        assertEquals(3L, response.illustrations().pending());
        assertEquals(2L, response.illustrations().retryScheduled());
        assertEquals(4L, response.analyses().pending());
        assertEquals(1L, response.analyses().retryScheduled());
        assertEquals(4L, response.recaps().pending());
        assertEquals(3L, response.recaps().retryScheduled());
        assertEquals(30L, response.totals().completed());
        assertEquals(10L, response.totals().failed());
    }
}
