package org.example.reader.cli;

import org.example.reader.service.PreGenerationService;
import org.example.reader.service.PreGenerationService.PreGenResult;
import org.example.reader.service.CuratedCatalogService;
import org.example.reader.service.CuratedCatalogService.CuratedCatalogBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Command-line runner for batch pre-generation of assets for the top 20 public domain books.
 *
 * Run with: mvn spring-boot:run -Dspring-boot.run.profiles=pregen-batch
 * Or: java -jar target/reader.jar --spring.profiles.active=pregen-batch
 */
@Component
@Profile("pregen-batch")
public class PreGenerationBatchRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PreGenerationBatchRunner.class);

    private final PreGenerationService preGenerationService;
    private final CuratedCatalogService curatedCatalogService;

    @Value("${pregen.cooldown-minutes:3}")
    private int cooldownMinutes;

    @Value("${pregen.cooldown-skip-under-images:20}")
    private int cooldownSkipUnderImages;

    @Value("${pregen.batch.mode:full}")
    private String batchMode;

    @Value("${pregen.batch.limit:20}")
    private int batchLimit;

    public PreGenerationBatchRunner(
            PreGenerationService preGenerationService,
            CuratedCatalogService curatedCatalogService) {
        this.preGenerationService = preGenerationService;
        this.curatedCatalogService = curatedCatalogService;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("========================================");
        log.info("Pre-Generation Batch Runner");
        log.info("========================================");
        List<CuratedCatalogBook> curatedBooks = curatedCatalogService.getPopularBooks();
        int effectiveLimit = Math.max(1, Math.min(batchLimit, curatedBooks.size()));
        List<CuratedCatalogBook> booksToProcess = curatedBooks.subList(0, effectiveLimit);
        log.info("Mode: {}", getBatchMode());
        log.info("Processing {} books with {}min cooldown between books", booksToProcess.size(), cooldownMinutes);
        log.info("");

        List<PreGenResult> results = new ArrayList<>();
        int bookNumber = 0;

        for (CuratedCatalogBook book : booksToProcess) {
            bookNumber++;
            log.info("========================================");
            log.info("[{}/{}] Processing: '{}' by {}", bookNumber, booksToProcess.size(), book.title(), book.author());
            log.info("Gutenberg ID: {}", book.gutenbergId());
            log.info("========================================");

            PreGenResult result;
            try {
                result = "recaps".equals(getBatchMode())
                        ? preGenerationService.preGenerateRecapsByGutenbergId(book.gutenbergId())
                        : preGenerationService.preGenerateByGutenbergId(book.gutenbergId());
                results.add(result);

                log.info("Result for '{}': {}", book.title(), result.message());
                log.info("  - Chapters: {}", result.chaptersProcessed());
                log.info("  - Illustrations: {} completed, {} failed",
                        result.illustrationsCompleted(), result.illustrationsFailed());
                log.info("  - Portraits: {} completed, {} failed",
                        result.portraitsCompleted(), result.portraitsFailed());
                log.info("  - Recaps: {} completed, {} failed",
                        result.recapsCompleted(), result.recapsFailed());

            } catch (Exception e) {
                log.error("Failed to process '{}': {}", book.title(), e.getMessage(), e);
                result = PreGenResult.failure("Exception: " + e.getMessage());
                results.add(result);
            }

            // Cooldown between books (skip after last book)
            if (bookNumber < booksToProcess.size() && cooldownMinutes > 0) {
                if ("recaps".equals(getBatchMode())) {
                    continue;
                }
                int totalImages = result.newIllustrations() + result.newPortraits();
                if (totalImages < cooldownSkipUnderImages) {
                    log.info("");
                    log.info("Skipping cooldown ({} new images < threshold {} for '{}')",
                            totalImages, cooldownSkipUnderImages, book.title());
                    continue;
                }
                log.info("");
                log.info("Cooling down for {} minutes to prevent overheating...", cooldownMinutes);
                try {
                    Thread.sleep(cooldownMinutes * 60 * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Cooldown interrupted, continuing...");
                }
            }
        }

        // Print summary
        log.info("");
        log.info("========================================");
        log.info("BATCH COMPLETE - SUMMARY");
        log.info("========================================");

        int successful = 0;
        int failed = 0;
        int totalIllustrations = 0;
        int totalPortraits = 0;
        int totalRecaps = 0;

        for (int i = 0; i < results.size(); i++) {
            PreGenResult result = results.get(i);
            CuratedCatalogBook book = booksToProcess.get(i);
            String status = result.success() ? "OK" : "FAILED";
            log.info("[{}] {} - {} illustrations, {} portraits",
                    status, book.title(),
                    result.illustrationsCompleted(), result.portraitsCompleted());
            log.info("      {} recaps", result.recapsCompleted());

            if (result.success()) {
                successful++;
            } else {
                failed++;
            }
            totalIllustrations += result.illustrationsCompleted();
            totalPortraits += result.portraitsCompleted();
            totalRecaps += result.recapsCompleted();
        }

        log.info("----------------------------------------");
        log.info("Books: {} successful, {} failed", successful, failed);
        log.info("Total illustrations generated: {}", totalIllustrations);
        log.info("Total portraits generated: {}", totalPortraits);
        log.info("Total recaps generated: {}", totalRecaps);
        log.info("========================================");
    }

    private String getBatchMode() {
        String normalized = batchMode == null ? "" : batchMode.trim().toLowerCase();
        return normalized.equals("recaps") ? "recaps" : "full";
    }
}
