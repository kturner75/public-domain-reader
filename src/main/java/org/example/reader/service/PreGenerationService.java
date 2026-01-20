package org.example.reader.service;

import org.example.reader.entity.CharacterStatus;
import org.example.reader.entity.ChapterEntity;
import org.example.reader.entity.ChapterAnalysisStatus;
import org.example.reader.entity.IllustrationStatus;
import org.example.reader.model.Book;
import org.example.reader.repository.BookRepository;
import org.example.reader.repository.CharacterRepository;
import org.example.reader.repository.ChapterAnalysisRepository;
import org.example.reader.repository.ChapterRepository;
import org.example.reader.repository.IllustrationRepository;
import org.example.reader.service.BookImportService.ImportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for pre-generating all assets (illustrations and character portraits) for a book.
 * Handles importing the book if missing, analyzing style, and queuing all generation tasks.
 */
@Service
public class PreGenerationService {

    private static final Logger log = LoggerFactory.getLogger(PreGenerationService.class);

    private final BookImportService bookImportService;
    private final BookStorageService bookStorageService;
    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final ChapterAnalysisRepository chapterAnalysisRepository;
    private final IllustrationRepository illustrationRepository;
    private final CharacterRepository characterRepository;
    private final IllustrationService illustrationService;
    private final CharacterService characterService;
    private final CharacterPrefetchService characterPrefetchService;

    @Value("${pregen.poll-interval-seconds:10}")
    private int pollIntervalSeconds;

    @Value("${pregen.max-wait-minutes:120}")
    private int maxWaitMinutes;

    @Value("${pregen.cooldown-every-images:100}")
    private int cooldownEveryImages;

    @Value("${pregen.image-cooldown-minutes:3}")
    private int imageCooldownMinutes;

    public PreGenerationService(
            BookImportService bookImportService,
            BookStorageService bookStorageService,
            BookRepository bookRepository,
            ChapterRepository chapterRepository,
            ChapterAnalysisRepository chapterAnalysisRepository,
            IllustrationRepository illustrationRepository,
            CharacterRepository characterRepository,
            IllustrationService illustrationService,
            CharacterService characterService,
            CharacterPrefetchService characterPrefetchService) {
        this.bookImportService = bookImportService;
        this.bookStorageService = bookStorageService;
        this.bookRepository = bookRepository;
        this.chapterRepository = chapterRepository;
        this.chapterAnalysisRepository = chapterAnalysisRepository;
        this.illustrationRepository = illustrationRepository;
        this.characterRepository = characterRepository;
        this.illustrationService = illustrationService;
        this.characterService = characterService;
        this.characterPrefetchService = characterPrefetchService;
    }

    public record PreGenResult(
            boolean success,
            String bookId,
            String bookTitle,
            String message,
            int chaptersProcessed,
            int illustrationsCompleted,
            int illustrationsFailed,
            int portraitsCompleted,
            int portraitsFailed,
            int newIllustrations,
            int newPortraits
    ) {
        public static PreGenResult failure(String message) {
            return new PreGenResult(false, null, null, message, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    /**
     * Pre-generate all assets for a book by its Gutenberg ID.
     * Will import the book if not already present.
     */
    public PreGenResult preGenerateByGutenbergId(int gutenbergId) {
        log.info("Starting pre-generation for Gutenberg ID: {}", gutenbergId);

        // Check if already imported
        String sourceId = String.valueOf(gutenbergId);
        Optional<Book> existingBook = bookStorageService.findBySource("gutenberg", sourceId);

        String bookId;
        String bookTitle;

        if (existingBook.isPresent()) {
            bookId = existingBook.get().id();
            bookTitle = existingBook.get().title();
            log.info("Book already imported: '{}' ({})", bookTitle, bookId);
        } else {
            log.info("Importing book from Gutenberg ID: {}", gutenbergId);
            ImportResult importResult = bookImportService.importBook(gutenbergId);
            if (!importResult.success()) {
                log.error("Failed to import book: {}", importResult.message());
                return PreGenResult.failure("Import failed: " + importResult.message());
            }
            bookId = importResult.bookId();
            bookTitle = bookRepository.findById(bookId).map(b -> b.getTitle()).orElse("Unknown");
            log.info("Successfully imported '{}' with {} chapters", bookTitle, importResult.chapterCount());
        }

        return preGenerateForBook(bookId);
    }

    /**
     * Pre-generate all assets for a book that's already in the database.
     */
    public PreGenResult preGenerateForBook(String bookId) {
        var book = bookRepository.findById(bookId).orElse(null);
        if (book == null) {
            return PreGenResult.failure("Book not found: " + bookId);
        }

        String bookTitle = book.getTitle();
        log.info("Starting pre-generation for '{}' by {}", bookTitle, book.getAuthor());

        int preIllustrationsTotal = illustrationRepository
                .findByChapterBookIdAndStatus(bookId, IllustrationStatus.COMPLETED).size()
                + illustrationRepository.findByChapterBookIdAndStatus(bookId, IllustrationStatus.FAILED).size();
        int prePortraitsTotal = characterRepository.findByBookIdAndStatus(bookId, CharacterStatus.COMPLETED).size()
                + characterRepository.findByBookIdAndStatus(bookId, CharacterStatus.FAILED).size();

        // Step 1: Analyze book style (if not already done)
        log.info("[1/4] Analyzing book illustration style...");
        illustrationService.getOrAnalyzeBookStyle(bookId, false);

        // Step 2: Prefetch main characters
        log.info("[2/4] Prefetching main characters...");
        characterPrefetchService.prefetchCharactersForBook(bookId);

        // Step 3: Queue all chapter illustrations and character analysis
        List<ChapterEntity> chapters = chapterRepository.findByBookIdOrderByChapterIndex(bookId);
        log.info("[3/4] Queuing generation for {} chapters...", chapters.size());

        for (ChapterEntity chapter : chapters) {
            illustrationService.requestIllustration(chapter.getId());
            characterService.requestChapterAnalysis(chapter.getId());
        }

        // Force-queue any items that were already pending (from previous runs)
        // This ensures they get processed even if they're less than 5 minutes old
        int illustrationsRequeued = illustrationService.forceQueuePendingForBook(bookId);
        int portraitsRequeued = characterService.forceQueuePendingPortraitsForBook(bookId);
        int analysesRequeued = characterService.forceQueuePendingAnalysesForBook(bookId);
        if (illustrationsRequeued > 0 || portraitsRequeued > 0 || analysesRequeued > 0) {
            log.info("Re-queued {} illustrations, {} portraits, and {} analyses from previous pending state",
                    illustrationsRequeued, portraitsRequeued, analysesRequeued);
        }

        // Step 4: Wait for all generation to complete
        log.info("[4/4] Waiting for generation to complete (polling every {}s, max {}min)...",
                pollIntervalSeconds, maxWaitMinutes);
        if (cooldownEveryImages > 0 && imageCooldownMinutes > 0) {
            log.info("  Periodic cooldown: {}min every {} images", imageCooldownMinutes, cooldownEveryImages);
        }

        long startTime = System.currentTimeMillis();
        long maxWaitMs = maxWaitMinutes * 60 * 1000L;

        int lastIllustrationsPending = -1;
        int lastPortraitsPending = -1;
        int lastAnalysesPending = -1;
        int stallCount = 0;
        final int stallThreshold = 6; // After 6 unchanged polls (60s), consider it stalled

        // Track images completed for periodic cooldown
        int imagesSinceLastCooldown = 0;
        int lastTotalCompleted = preIllustrationsTotal + prePortraitsTotal;

        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            // Count pending/generating illustrations
            int illustrationsPending = illustrationRepository.findByChapterBookIdAndStatus(bookId, IllustrationStatus.PENDING).size();
            int illustrationsGenerating = illustrationRepository.findByChapterBookIdAndStatus(bookId, IllustrationStatus.GENERATING).size();

            // Count pending/generating portraits
            int portraitsPending = characterRepository.findByBookIdAndStatus(bookId, CharacterStatus.PENDING).size();
            int portraitsGenerating = characterRepository.findByBookIdAndStatus(bookId, CharacterStatus.GENERATING).size();

            // Count pending/generating chapter analyses
            int analysesPending = chapterAnalysisRepository.findByChapterBookIdAndStatus(bookId, ChapterAnalysisStatus.PENDING).size();
            int analysesPendingNull = chapterAnalysisRepository.findByChapterBookIdAndStatusIsNull(bookId).size();
            int analysesGenerating = chapterAnalysisRepository.findByChapterBookIdAndStatus(bookId, ChapterAnalysisStatus.GENERATING).size();

            int totalPending = illustrationsPending + illustrationsGenerating
                    + portraitsPending + portraitsGenerating
                    + analysesPending + analysesPendingNull + analysesGenerating;

            // Log progress if changed
            if (illustrationsPending + illustrationsGenerating != lastIllustrationsPending ||
                    portraitsPending + portraitsGenerating != lastPortraitsPending ||
                    analysesPending + analysesPendingNull + analysesGenerating != lastAnalysesPending) {
                log.info("  Progress: {} illustrations pending/generating, {} portraits pending/generating, {} analyses pending/generating",
                        illustrationsPending + illustrationsGenerating,
                        portraitsPending + portraitsGenerating,
                        analysesPending + analysesPendingNull + analysesGenerating);
                lastIllustrationsPending = illustrationsPending + illustrationsGenerating;
                lastPortraitsPending = portraitsPending + portraitsGenerating;
                lastAnalysesPending = analysesPending + analysesPendingNull + analysesGenerating;
                stallCount = 0; // Reset stall counter on progress
            } else if (totalPending > 0) {
                stallCount++;
                if (stallCount >= stallThreshold) {
                    log.warn("Generation appears stalled. Re-queuing {} stuck items...", totalPending);
                    // Reset stuck GENERATING items and re-queue everything
                    int illustrationsReset = illustrationService.resetAndRequeueStuckForBook(bookId);
                    int portraitsReset = characterService.resetAndRequeueStuckPortraitsForBook(bookId);
                    int analysesReset = characterService.resetAndRequeueStuckAnalysesForBook(bookId);
                    log.info("Reset and re-queued {} illustrations, {} portraits, and {} analyses",
                            illustrationsReset, portraitsReset, analysesReset);
                    stallCount = 0;
                }
            }

            if (totalPending == 0) {
                log.info("All generation tasks completed!");
                break;
            }

            // Check for periodic cooldown based on images completed
            if (cooldownEveryImages > 0 && imageCooldownMinutes > 0) {
                int currentCompleted = illustrationRepository.findByChapterBookIdAndStatus(bookId, IllustrationStatus.COMPLETED).size()
                        + illustrationRepository.findByChapterBookIdAndStatus(bookId, IllustrationStatus.FAILED).size()
                        + characterRepository.findByBookIdAndStatus(bookId, CharacterStatus.COMPLETED).size()
                        + characterRepository.findByBookIdAndStatus(bookId, CharacterStatus.FAILED).size();
                int newlyCompleted = currentCompleted - lastTotalCompleted;
                imagesSinceLastCooldown += newlyCompleted;
                lastTotalCompleted = currentCompleted;

                if (imagesSinceLastCooldown >= cooldownEveryImages && totalPending > 0) {
                    log.info("Cooldown triggered: {} images completed since last cooldown (threshold: {})",
                            imagesSinceLastCooldown, cooldownEveryImages);
                    log.info("Cooling down for {} minutes to prevent API overheating...", imageCooldownMinutes);
                    try {
                        Thread.sleep(imageCooldownMinutes * 60 * 1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Cooldown interrupted");
                    }
                    imagesSinceLastCooldown = 0;
                    log.info("Cooldown complete, resuming generation...");
                }
            }

            try {
                Thread.sleep(pollIntervalSeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Pre-generation interrupted");
                break;
            }
        }

        // Collect final stats
        int illustrationsCompleted = illustrationRepository.findByChapterBookIdAndStatus(bookId, IllustrationStatus.COMPLETED).size();
        int illustrationsFailed = illustrationRepository.findByChapterBookIdAndStatus(bookId, IllustrationStatus.FAILED).size();
        int portraitsCompleted = characterRepository.findByBookIdAndStatus(bookId, CharacterStatus.COMPLETED).size();
        int portraitsFailed = characterRepository.findByBookIdAndStatus(bookId, CharacterStatus.FAILED).size();
        int newIllustrations = Math.max(0, (illustrationsCompleted + illustrationsFailed) - preIllustrationsTotal);
        int newPortraits = Math.max(0, (portraitsCompleted + portraitsFailed) - prePortraitsTotal);

        long elapsedMinutes = (System.currentTimeMillis() - startTime) / 60000;
        log.info("Pre-generation completed for '{}' in {} minutes", bookTitle, elapsedMinutes);
        log.info("  Illustrations: {} completed, {} failed", illustrationsCompleted, illustrationsFailed);
        log.info("  Portraits: {} completed, {} failed", portraitsCompleted, portraitsFailed);

        boolean allSuccess = illustrationsFailed == 0 && portraitsFailed == 0;
        String message = allSuccess ? "All assets generated successfully" :
                String.format("%d illustrations failed, %d portraits failed", illustrationsFailed, portraitsFailed);

        return new PreGenResult(
                allSuccess,
                bookId,
                bookTitle,
                message,
                chapters.size(),
                illustrationsCompleted,
                illustrationsFailed,
                portraitsCompleted,
                portraitsFailed,
                newIllustrations,
                newPortraits
        );
    }
}
